package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsFieldDecl
import org.rust.lang.core.psi.RsStructExprBody
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
        val expr = startElement as RsStructExprBody
        val nExistingFields = expr.structExprFieldList.size
        val newBody = psiFactory.createStructExprBody(fieldsToAdd.mapNotNull { it.name })
        val firstNewField = newBody.lbrace.nextSibling ?: return
        val lastNewField = newBody.rbrace?.prevSibling ?: return

        expr.ensureTrailingComma()
        expr.addRangeBefore(firstNewField, lastNewField, expr.rbrace)

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
}
