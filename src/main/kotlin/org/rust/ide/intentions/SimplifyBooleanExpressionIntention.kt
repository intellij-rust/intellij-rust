package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.ide.formatter.impl.UNARY_OPS
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.parentOfType

class SimplifyBooleanExpressionIntention : RustElementBaseIntentionAction<SimplifyBooleanExpressionIntention.Context>() {
    override fun getText() = "Simplify boolean expression"
    override fun getFamilyName() = text

    data class Context(
        val expr: RustExprElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        var expr = element.parentOfType<RustExprElement>() ?: return null
        var lastSimplifiable: RustExprElement? = null
        if (isSimplifiableExpression(expr))
            lastSimplifiable = expr

        while (true) {
            expr = expr.parentOfType<RustExprElement>() ?: break
            if (isSimplifiableExpression(expr))
                lastSimplifiable = expr
        }

        return lastSimplifiable?.let { Context(it) }
    }

    private fun isSimplifiableExpression(psi: PsiElement): Boolean {
        return when (psi) {
            is RustLitExprElement ->
                psi.`true` != null || psi.`false` != null
            is RustBinaryExprElement -> {
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
            is RustUnaryExprElement ->
                when (psi.operatorType) {
                    EXCL -> psi.expr?.let { isSimplifiableExpression(it) } ?: false
                    else -> false
                }
            is RustParenExprElement ->
                isSimplifiableExpression(psi.expr)
            else ->
                false
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (expr) = ctx;
        val value = calculateExpression(expr) ?: return
        expr.replace(RustPsiFactory(project).createExpression("" + value))
    }

    private fun calculateExpression(expr: RustExprElement): Boolean? {
        return when (expr) {
            is RustBinaryExprElement ->
                when (expr.operatorType) {
                    ANDAND -> {
                        val rhs = expr.right ?: return null
                        val leftValue = calculateExpression(expr.left) ?: return null
                        if (!leftValue)
                            return false
                        val rightValue = calculateExpression(rhs) ?: return null
                        leftValue && rightValue
                    }
                    OROR -> {
                        val rhs = expr.right ?: return null
                        val leftValue = calculateExpression(expr.left) ?: return null
                        if (leftValue)
                            return true
                        val rightValue = calculateExpression(rhs) ?: return null
                        leftValue || rightValue
                    }
                    XOR -> {
                        val rhs = expr.right ?: return null
                        val leftValue = calculateExpression(expr.left) ?: return null
                        val rightValue = calculateExpression(rhs) ?: return null
                        leftValue xor rightValue
                    }
                    else -> null
                }
            is RustUnaryExprElement -> {
                when (expr.operatorType) {
                    EXCL -> expr.expr?.let { calculateExpression(it)?.let { !it } }
                    else -> null
                }
            }
            is RustParenExprElement ->
                calculateExpression(expr.expr)
            is RustLitExprElement -> {
                if (expr.`false` != null)
                    return false
                if (expr.`true` != null)
                    return true
                null
            }
            else -> null
        }
    }

    val RustUnaryExprElement.operator: PsiElement
        get() = requireNotNull(node.findChildByType(UNARY_OPS)) { "guaranteed to be not-null by parser" }.psi

    val RustUnaryExprElement.operatorType: IElementType
        get() = operator.elementType

}
