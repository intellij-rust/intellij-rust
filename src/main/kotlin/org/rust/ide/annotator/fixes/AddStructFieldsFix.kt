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
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.getNextNonCommentSibling

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
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
        val psiFactory = RustPsiFactory(project)
        var expr = startElement as RsStructExprBody
        val nExistingFields = expr.structExprFieldList.size
        val multiline = nExistingFields == 0 || expr.textContains('\n')

        for (fieldDecl in fieldsToAdd) {
            val field = psiFactory.createStructExprField(fieldDecl.name!!)
            expr.ensureTrailingComma()
            expr.addFieldBefore(field, null, multiline)
        }

        expr = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(expr)
        RsCommaFormatProcessor.fixStructExprBody(expr)

        if (editor != null) {
            val firstExpression = expr.structExprFieldList[nExistingFields].expr
                ?: error("Invalid struct expr body: `${expr.text}`")
            editor.caretModel.moveToOffset(firstExpression.textOffset)
        }
    }

    private fun RsStructExprBody.ensureTrailingComma() {
        val lastField = structExprFieldList.lastOrNull()
            ?: return

        if (lastField.getNextNonCommentSibling()?.elementType == COMMA) return
        addAfter(RustPsiFactory(project).createComma(), lastField)
    }

    private fun RsStructExprBody.addFieldBefore(
        newField: RsStructExprField,
        anchor: RsStructExprBody?,
        multiline: Boolean
    ): RsStructExprField {

        check(anchor == null || anchor.parent == this)
        val psiFactory = RustPsiFactory(newField.project)

        val comma = addBefore(psiFactory.createComma(), anchor ?: rbrace)
        val result = addBefore(newField, comma) as RsStructExprField
        if (multiline) {
            addBefore(psiFactory.createNewline(), result)
        }
        return result
    }
}
