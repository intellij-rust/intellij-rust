package org.rust.ide.annotator.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.psi.ext.parentOfType

class AddTurbofishFix : RsElementBaseIntentionAction<AddTurbofishFix.Context>() {
    private val TURBOFISH = "::"

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, caller, gen, more) = ctx
        val callWithNamespace =
            RsPsiFactory(project).createExpression("""${caller.text}$TURBOFISH<${gen.text}>${more.text}""")
        matchExpr.replace(callWithNamespace)
    }

    data class Context(
        val matchExpr: RsBinaryExpr,
        val caller: RsExpr,
        val gen: RsPathExpr,
        val more: RsExpr
    )

    override fun getText() = "Add turbofish operator"
    override fun getFamilyName() = text

    private fun resolveMatchExpression(element: PsiElement): RsBinaryExpr? {
        val base = element.parentOfType<RsBinaryExpr>() ?: return null
        return if (base.left is RsBinaryExpr) {
            base
        } else {
            base.parentOfType<RsBinaryExpr>()
        }
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = resolveMatchExpression(element) ?: return null
        val left = matchExpr.left as? RsBinaryExpr ?: return null
        if (left.operatorType != LT || matchExpr.operatorType != GT) {
            return null
        }
        val caller = left.left
        val gen = left.right as? RsPathExpr ?: return null
        val more = matchExpr.right ?: return null
        if (!isCallExpression(more)) {
            return null
        };
        return Context(matchExpr, caller, gen, more)
    }

    private fun isCallExpression(expr: RsExpr) = expr is RsParenExpr || expr.firstChild is RsParenExpr
}
