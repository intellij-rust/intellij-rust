package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustParenExprElement
import org.rust.lang.core.psi.util.parentOfType

class RemoveParenthesesFromExprIntention : RustElementBaseIntentionAction<RustParenExprElement>() {
    override fun getText(): String = "Remove parentheses from expression"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustParenExprElement? =
        element.parentOfType<RustParenExprElement>()

    override fun invoke(project: Project, editor: Editor, ctx: RustParenExprElement) {
        ctx.replace(ctx.expr)
    }
}
