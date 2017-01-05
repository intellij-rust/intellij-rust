package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RustFieldDeclElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustStructExprBodyElement

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
    val fieldsToAdd: List<RustFieldDeclElement>,
    structBody: RustStructExprBodyElement
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
        val expr = startElement as RustStructExprBodyElement
        val newBody = RustPsiFactory(project).createStructExprBody(fieldsToAdd.mapNotNull { it.name })
        val firstNewField = newBody.lbrace.nextSibling ?: return
        val lastNewField = newBody.rbrace?.prevSibling ?: return
        expr.addRangeAfter(firstNewField, lastNewField, expr.lbrace)
        if (editor != null) {
            val firstExpression = expr.structExprFieldList.first().expr
                ?: error("Invalid struct expr body: `${expr.text}`")
            editor.caretModel.moveToOffset(firstExpression.textOffset)
        }
    }
}
