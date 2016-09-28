package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustParenExprElement
import org.rust.lang.core.psi.util.parentOfType

class RemoveParenthesesFromExprIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String = "Remove parentheses from expression"
    override fun getFamilyName(): String = text
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!element.isWritable) return false

        element.parentOfType<RustParenExprElement>() ?: return false
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val parentExpr = element.parentOfType<RustParenExprElement>() ?: return
        parentExpr.replace(parentExpr.expr)
    }
}
