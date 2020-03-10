/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.asBool
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.utils.evaluation.evaluate
import org.rust.lang.utils.negate

class BooleanExprSimplifier(val project: Project) {
    private val factory = RsPsiFactory(project)

    /**
     * Simplifies a boolean expression if can.
     *
     * @returns `null` if expr cannot be simplified, `expr` otherwise
     */
    fun simplify(expr: RsExpr): RsExpr? {
        if (expr is RsLitExpr) return null

        val value = eval(expr)
        if (value != null) {
            return factory.createExpression(value.toString())
        }

        return when (expr) {
            is RsBinaryExpr -> {
                val left = expr.left
                val right = expr.right ?: return null
                val op = expr.operatorType

                val lhs = simplify(left) ?: left
                val rhs = simplify(right) ?: right

                when {
                    lhs is RsLitExpr -> simplifyBinaryOperation(op, lhs, rhs)
                    rhs is RsLitExpr -> simplifyBinaryOperation(op, rhs, lhs)
                    else -> factory.createExpression("${lhs.text} ${expr.binaryOp.text} ${rhs.text}")
                }
            }

            is RsUnaryExpr -> {
                val parenExpr = expr.expr as? RsParenExpr ?: return expr
                val interior = parenExpr.expr
                if (expr.operatorType == UnaryOperator.NOT && interior is RsBinaryExpr) {
                    interior.negate() as RsExpr
                } else {
                    null
                }
            }

            is RsParenExpr -> {
                val interiorSimplified = simplify(expr.expr)
                interiorSimplified?.let { factory.createExpression("(${it.text})") }
            }

            else -> null
        }
    }

    private fun simplifyBinaryOperation(op: BinaryOperator, const: RsLitExpr, expr: RsExpr): RsExpr? {
        val literal = const.boolLiteral?.text ?: return null
        return when (op) {
            LogicOp.AND -> if (literal == "false") factory.createExpression("false") else expr
            LogicOp.OR -> if (literal == "true") factory.createExpression("true") else expr
            EqualityOp.EQ -> if (literal == "false") factory.createExpression("!${expr.text}") else expr
            EqualityOp.EXCLEQ -> if (literal == "true") factory.createExpression("!${expr.text}") else expr
            else -> null
        }
    }

    companion object {
        fun canBeSimplified(expr: RsExpr): Boolean {
            if (expr is RsLitExpr) return false

            if (canBeEvaluated(expr)) return true

            when (expr) {
                is RsBinaryExpr -> {
                    val left = expr.left
                    val right = expr.right ?: return false

                    if (expr.operatorType in setOf(LogicOp.AND, LogicOp.OR, EqualityOp.EQ, EqualityOp.EXCLEQ)) {
                        if (canBeSimplified(left) || canBeSimplified(right)) return true
                        if (canBeEvaluated(left) || canBeEvaluated(right)) return true
                    }
                }

                is RsParenExpr -> return canBeSimplified(expr.expr)

                is RsUnaryExpr -> {
                    if (expr.operatorType != UnaryOperator.NOT) {
                        return false
                    }
                    val parenExpr = expr.expr as? RsParenExpr ?: return false
                    val binOp = (parenExpr.expr as? RsBinaryExpr)?.operatorType ?: return false
                    return binOp is EqualityOp || binOp is ComparisonOp
                }
            }

            return false
        }

        private fun canBeEvaluated(expr: RsExpr): Boolean = eval(expr) != null

        private fun eval(expr: RsExpr): Boolean? = expr.evaluate(TyBool, resolver = null).asBool()
    }
}
