package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.template.postfix.isBool
import org.rust.ide.utils.UnaryOperator
import org.rust.ide.utils.operatorType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.type

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
        return (psi.copy() as RsExpr).simplify().second
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val (expr, isSimplified) = ctx.simplify()
        if (isSimplified)
            ctx.replace(expr)
    }

    private fun createPsiElement(project: Project, value: Any) = RsPsiFactory(project).createExpression(value.toString())

    private fun RsExpr.simplify(): Pair<RsExpr, Boolean> {
        if (this is RsLitExpr)
            return this to false
        return this.eval()?.let {
            createPsiElement(project, it) to true
        } ?: when (this) {
            is RsBinaryExpr -> {
                val (leftExpr, leftSimplified) = left.simplify()
                val (rightExpr, rightSimplified) = right!!.simplify()
                if (leftExpr is RsLitExpr) {
                    leftExpr.boolLiteral?.let {
                        when (this.operatorType) {
                            ANDAND ->
                                when (it.text) {
                                    "true" -> return rightExpr to true
                                    "false" -> return createPsiElement(project, "false") to true
                                }
                            OROR ->
                                when (it.text) {
                                    "true" -> return createPsiElement(project, "true") to true
                                    "false" -> return rightExpr to true
                                }
                        }
                        {}
                    }
                }
                if (rightExpr is RsLitExpr) {
                    rightExpr.boolLiteral?.let {
                        when (this.operatorType) {
                            ANDAND ->
                                when (it.text) {
                                    "true" -> return leftExpr to true
                                    "false" -> return createPsiElement(project, "false") to true
                                }
                            OROR ->
                                when (it.text) {
                                    "true" -> return createPsiElement(project, "true") to true
                                    "false" -> return leftExpr to true
                                }
                        }
                        {}
                    }
                }
                if (leftSimplified)
                    this.left.replace(leftExpr)
                if (rightSimplified)
                    this.right!!.replace(rightExpr)
                this to (leftSimplified || rightSimplified)
            }
            else ->
                this to false
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
