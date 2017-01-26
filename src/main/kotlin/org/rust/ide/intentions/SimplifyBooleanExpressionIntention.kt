package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.SimplifyBooleanExpressionIntention.UnaryOperator.*
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

    private fun isSimplifiableExpression(psi: PsiElement): Boolean {
        return when (psi) {
            is RsLitExpr -> psi.boolLiteral != null
            is RsBinaryExpr -> {
                val leftSimplifiable = isSimplifiableExpression(psi.left)
                val fullSimplifiable =
                    leftSimplifiable && psi.right?.let { isSimplifiableExpression(it) } ?: true
                if (fullSimplifiable)
                    return true
                return when (psi.operatorType) {
                    ANDAND, OROR -> leftSimplifiable // short-circuit operations
                    else -> false
                }
            }
            is RsUnaryExpr ->
                when (psi.operatorType) {
                    UnaryOperator.NOT -> psi.expr?.let { isSimplifiableExpression(it) } ?: false
                    else -> false
                }
            is RsParenExpr ->
                isSimplifiableExpression(psi.expr)
            else ->
                false
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val value = ctx.eval() ?: return
        ctx.replace(RsPsiFactory(project).createExpression(value.toString()))
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

    /**
     * Enum class representing unary operator in rust.
     *
     * [REF]     is a `&` operator (`&a`)
     *
     * [REF_MUT] is a `&mut` operator (`&mut a`)
     *
     * [DEREF]   is a `*` operator (`*a`)
     *
     * [MINUS]   is a `-` operator (`-a`)
     *
     * [NOT]     is a `!` operator (`!a`)
     *
     * [BOX]     is a `box` operator (`box a`)
     */
    enum class UnaryOperator {
        REF, // take reference
        REF_MUT, // take mutable reference
        DEREF, // dereference
        MINUS, // unary minus
        NOT, // negation
        BOX         // boxing
    }

    /**
     * Operator of current psi node with unary operation.
     *
     * The result can be [REF] (`&`), [REF_MUT] (`&mut`),
     * [DEREF] (`*`), [MINUS] (`-`), [NOT] (`!`),
     * [BOX] (`box`) or `null` if none of these.
     */
    val RsUnaryExpr.operatorType: UnaryOperator?
        get() = when {
            this.and != null -> UnaryOperator.REF
            this.mut != null -> UnaryOperator.REF_MUT
            this.mul != null -> UnaryOperator.DEREF
            this.minus != null -> UnaryOperator.MINUS
            this.excl != null -> UnaryOperator.NOT
            this.box != null -> UnaryOperator.BOX
            else -> null
        }

}
