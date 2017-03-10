package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.parentOfType

open class RemoveMutableIntention : RsElementBaseIntentionAction<RemoveMutableIntention.Context>(){
    override fun getText() = "Remove mutable"
    override fun getFamilyName() = text

    open val mutable = true

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newLetExpr = RsPsiFactory(project).createLetDeclaration(ctx.letExpr.pat!!.firstChild.lastChild.text, ctx.letExpr.expr!!, !mutable, ctx.letExpr.typeReference)
        ctx.letExpr.replace(newLetExpr)
    }

    protected fun isMutable(letExpr: RsLetDecl): Boolean {
        val pat = letExpr.pat?.firstChild?.firstChild ?: return false
        return pat.text == "mut"
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val letDecl = element.parentOfType<RsLetDecl>() ?: return null
        if (isMutable(letDecl) != mutable) return null
        return Context(letDecl)
    }

    data class Context(
        val letExpr: RsLetDecl
    )
}
