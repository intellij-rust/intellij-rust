package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.formatter.RsCommaFormatProcessor
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsStructExprBody
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.getNextNonCommentSibling

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
    val declaredFields: List<RsFieldDecl>,
    val fieldsToAdd: List<RsFieldDecl>,
    structBody: RsStructExprBody
) : LocalQuickFixAndIntentionActionOnPsiElement(structBody) {
    override fun getText(): String = "Add missing fields"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val psiFactory = RsPsiFactory(project)
        var expr = startElement as RsStructExprBody
        val multiline = (expr.structExprFieldList.isEmpty() && fieldsToAdd.size > 1) || expr.textContains('\n')

        var firstAdded: RsStructExprField? = null
        for (fieldDecl in fieldsToAdd) {
            val field = psiFactory.createStructExprField(fieldDecl.name!!)
            val addBefore = findPlaceToAdd(field, expr.structExprFieldList, declaredFields)
            expr.ensureTrailingComma()
            val added = expr.addFieldBefore(field, addBefore, multiline)
            if (firstAdded == null) {
                firstAdded = added
            }
        }

        expr = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(expr)
        RsCommaFormatProcessor.fixSingleLineBracedBlock(expr)

        if (editor != null && firstAdded != null) {
            editor.caretModel.moveToOffset(firstAdded.expr!!.textOffset)
        }
    }

    private fun findPlaceToAdd(
        fieldToAdd: RsStructExprField,
        existingFields: List<RsStructExprField>,
        declaredFields: List<RsFieldDecl>
    ): RsStructExprField? {
        // If `fieldToAdd` is first in the original declaration, add it first
        if (fieldToAdd.referenceName == declaredFields.firstOrNull()?.name) {
            return existingFields.firstOrNull()
        }

        // If it was last, add last
        if (fieldToAdd.referenceName == declaredFields.lastOrNull()?.name) {
            return null
        }

        val pos = declaredFields.indexOfFirst { it.name == fieldToAdd.referenceName }
        check(pos != -1)
        val prev = declaredFields[pos - 1]
        val next = declaredFields[pos + 1]
        val prevIdx = existingFields.indexOfFirst { it.referenceName == prev.name }
        val nextIdx = existingFields.indexOfFirst { it.referenceName == next.name }

        // Fit between two existing fields in the same order
        if (prevIdx != -1 && prevIdx + 1 == nextIdx) {
            return existingFields[nextIdx]
        }
        // We have next field, but the order is different.
        // It's impossible to guess the best position, so
        // let's add to the end
        if (nextIdx != -1) {
            return null
        }

        if (prevIdx != -1) {
            return existingFields.getOrNull(prevIdx + 1)
        }

        return null
    }

    private fun RsStructExprBody.ensureTrailingComma() {
        val lastField = structExprFieldList.lastOrNull()
            ?: return

        if (lastField.getNextNonCommentSibling()?.elementType == COMMA) return
        addAfter(RsPsiFactory(project).createComma(), lastField)
    }

    private fun RsStructExprBody.addFieldBefore(
        newField: RsStructExprField,
        anchor: RsStructExprField?,
        multiline: Boolean
    ): RsStructExprField {

        check(anchor == null || anchor.parent == this)
        val psiFactory = RsPsiFactory(newField.project)

        var comma = psiFactory.createComma()
        comma = addBefore(comma, anchor ?: rbrace)
        val result = addBefore(newField, comma) as RsStructExprField
        if (multiline) {
            addBefore(psiFactory.createNewline(), result)
            addAfter(psiFactory.createNewline(), result.nextSibling)
        }
        return result
    }
}
