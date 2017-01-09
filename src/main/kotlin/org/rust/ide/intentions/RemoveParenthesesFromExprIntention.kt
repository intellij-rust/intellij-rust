package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustParenExprElement
import org.rust.lang.core.psi.util.parentOfType

class RemoveParenthesesFromExprIntention : RustElementBaseIntentionAction() {
    override fun getText(): String = "Remove parentheses from expression"
    override fun getFamilyName(): String = text

    override fun invokeImpl(project: Project, editor: Editor, element: PsiElement) {
        val parentExpr = element.parentOfType<RustParenExprElement>() ?: return
        parentExpr.replace(parentExpr.expr)
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
        element.parentOfType<RustParenExprElement>() != null
}
