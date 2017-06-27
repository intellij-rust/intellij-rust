/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.formatter.RsTrailingCommaFormatProcessor
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteralBody
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
    val declaredFields: List<RsFieldDecl>,
    val fieldsToAdd: List<RsFieldDecl>,
    structBody: RsStructLiteralBody
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
        var expr = startElement as RsStructLiteralBody

        val forceMultiline = expr.structLiteralFieldList.isEmpty() && fieldsToAdd.size > 2

        var firstAdded: RsStructLiteralField? = null
        for (fieldDecl in fieldsToAdd) {
            val field = psiFactory.createStructLiteralField(fieldDecl.name!!)
            val addBefore = findPlaceToAdd(field, expr.structLiteralFieldList, declaredFields)
            expr.ensureTrailingComma()

            val comma = expr.addBefore(psiFactory.createComma(), addBefore ?: expr.rbrace)
            val added = expr.addBefore(field, comma) as RsStructLiteralField

            if (firstAdded == null) {
                firstAdded = added
            }
        }

        if (forceMultiline) {
            expr.addAfter(psiFactory.createNewline(), expr.lbrace)
        }

        expr = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(expr)
        RsTrailingCommaFormatProcessor.fixSingleLineBracedBlock(expr)

        if (editor != null && firstAdded != null) {
            editor.caretModel.moveToOffset(firstAdded.expr!!.textOffset)
        }
    }

    private fun findPlaceToAdd(
        fieldToAdd: RsStructLiteralField,
        existingFields: List<RsStructLiteralField>,
        declaredFields: List<RsFieldDecl>
    ): RsStructLiteralField? {
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

    private fun RsStructLiteralBody.ensureTrailingComma() {
        val lastField = structLiteralFieldList.lastOrNull()
            ?: return

        if (lastField.getNextNonCommentSibling()?.elementType == COMMA) return
        addAfter(RsPsiFactory(project).createComma(), lastField)
    }
}
