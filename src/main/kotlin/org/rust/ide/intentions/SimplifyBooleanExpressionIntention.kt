package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.utils.UnaryOperator
import org.rust.ide.utils.operatorType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.psi.util.parentOfType

class SimplifyBooleanExpressionIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText() = "Simplify boolean expression"
    override fun getFamilyName() = "Simplify boolean expression"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? =
            element.parentOfType<RsExpr>()
                    ?.ancestors
                    ?.takeWhile { it is RsExpr }
                    ?.map { it as RsExpr }
                    ?.findLast { isSimplifiableExpression(it) }

    private fun isSimplifiableExpression(psi: RsExpr): Boolean {
        if (psi !is RsLitExpr && psi.eval() != null) return true

        return when (psi) {
            is RsBinaryExpr -> when (psi.operatorType) {
            // short-circuit operations
                ANDAND, OROR -> psi.left.eval() != null && psi.right != null
                else -> false
            }
            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val value = ctx.eval()
        if (value != null) {
            ctx.replace(RsPsiFactory(project).createExpression(value.toString()))
            return
        }
        val expr = ctx as RsBinaryExpr
        val leftVal = ctx.left.eval()
            ?: error("Can't simplify expression")
        when (expr.operatorType) {
            ANDAND -> {
                check(leftVal)
                expr.replace(expr.right!!)
            }
            OROR -> {
                check(!leftVal)
                expr.replace(expr.right!!)
            }
        }
    }

    private fun RsExpr.eval(): Boolean? {
        return when (this) {
            is RsLitExpr ->
                (kind as? RsLiteralKind.Boolean)?.value

            is RsBinaryExpr -> when (operatorType) {
                ANDAND -> {
                    val lhs = left.eval() ?: return null
                    if (!lhs) return false
                    val rhs = right?.eval() ?: return null
                    lhs && rhs
                }
                OROR -> {
                    val lhs = left.eval() ?: return null
                    if (lhs) return true
                    val rhs = right?.eval() ?: return null
                    lhs || rhs
                }
                XOR -> {
                    val lhs = left.eval() ?: return null
                    val rhs = right?.eval() ?: return null
                    lhs xor rhs
                }
                else -> null
            }

            is RsUnaryExpr -> when (operatorType) {
                UnaryOperator.NOT -> expr?.eval()?.let { !it }
                else -> null
            }

            is RsParenExpr -> expr.eval()

            else -> null
        }
    }
}
