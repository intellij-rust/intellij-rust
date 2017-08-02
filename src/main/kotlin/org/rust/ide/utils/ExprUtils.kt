/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.LogicOp.*

/**
 * Returns `true` if all elements are `true`, `false` if there exists
 * `false` element and `null` otherwise.
 */
private fun <T> List<T>.allMaybe(predicate: (T) -> Boolean?): Boolean? {
    val values = map(predicate)
    val nullsTrue = values.all {
        it ?: true
    }
    val nullsFalse = values.all {
        it ?: false
    }
    return if (nullsTrue == nullsFalse) nullsTrue else null
}

/**
 * Check if an expression is functionally pure
 * (has no side-effects and throws no errors).
 *
 * @return `true` if the expression is pure, `false` if
 *        > it is not pure (has side-effects / throws errors)
 *        > or `null` if it is unknown.
 */
fun RsExpr.isPure(): Boolean? {
    return when (this) {
        is RsArrayExpr -> when (semicolon) {
            null -> exprList.allMaybe(RsExpr::isPure)
            else -> exprList[0].isPure() // Array literal of form [expr; size],
        // size is a compile-time constant, so it is always pure
        }
        is RsStructLiteral -> when (structLiteralBody.dotdot) {
            null -> structLiteralBody.structLiteralFieldList
                    .map { it.expr }
                    .allMaybe { it?.isPure() } // TODO: Why `it` can be null?
            else -> null // TODO: handle update case (`Point{ y: 0, z: 10, .. base}`)
        }
        is RsBinaryExpr -> when (operatorType) {
            is LogicOp -> listOfNotNull(left, right).allMaybe(RsExpr::isPure)
            else -> null // Have to search if operation is overloaded
        }
        is RsTupleExpr -> exprList.allMaybe(RsExpr::isPure)
        is RsDotExpr -> if (methodCall != null) null else expr.isPure()
        is RsParenExpr -> expr.isPure()
        is RsBreakExpr, is RsContExpr, is RsRetExpr, is RsTryExpr -> false   // Changes execution flow
        is RsPathExpr, is RsLitExpr, is RsUnitExpr -> true

    // TODO: more complex analysis of blocks of code and search of implemented traits
        is RsBlockExpr, // Have to analyze lines, very hard case
        is RsCastExpr, // `expr.isPure()` maybe not true, think about side-effects, may panic while cast
        is RsCallExpr, // All arguments and function itself must be pure, very hard case
        is RsForExpr, // Always return (), if pure then can be replaced with it
        is RsIfExpr,
        is RsIndexExpr, // Index trait can be overloaded, can panic if out of bounds
        is RsLambdaExpr,
        is RsLoopExpr,
        is RsMacroExpr,
        is RsMatchExpr,
        is RsRangeExpr,
        is RsUnaryExpr, // May be overloaded
        is RsWhileExpr -> null
        else -> null
    }
}


fun RsExpr.canBeSimplified(): Boolean =
    simplifyBooleanExpression(peek = true).second

fun RsExpr.simplifyBooleanExpression() =
    simplifyBooleanExpression(peek = false)

/**
 * Simplifies a boolean expression if can.
 *
 * @param peek if true then does not perform any changes on PSI,
 *             `expr` is not defined and `result` indicates if this expression
 *             can be simplified
 * @return `(expr, result)` where `expr` is a resulting expression,
 *         `result` is true if an expression was actually simplified.
 */
private fun RsExpr.simplifyBooleanExpression(peek: Boolean): Pair<RsExpr, Boolean> {
    val original = this to false
    if (this is RsLitExpr) return original

    val value = this.evalBooleanExpression()
    if (value != null) {
        return (if (peek) this else createPsiElement(project, value)) to true
    }

    return when (this) {
        is RsBinaryExpr -> {
            val right = right ?: return original
            val (leftExpr, leftSimplified) = left.simplifyBooleanExpression(peek)
            val (rightExpr, rightSimplified) = right.simplifyBooleanExpression(peek)
            if (leftExpr is RsLitExpr) {
                if (peek)
                    return this to true
                simplifyBinaryOperation(this, leftExpr, rightExpr, project)?.let {
                    return it to true
                }
            }
            if (rightExpr is RsLitExpr) {
                if (peek)
                    return this to true
                simplifyBinaryOperation(this, rightExpr, leftExpr, project)?.let {
                    return it to true
                }
            }
            if (!peek) {
                if (leftSimplified)
                    left.replace(leftExpr)
                if (rightSimplified)
                    right.replace(rightExpr)
            }
            this to (leftSimplified || rightSimplified)
        }
        else -> original
    }
}

private fun simplifyBinaryOperation(op: RsBinaryExpr, const: RsLitExpr, expr: RsExpr, project: Project): RsExpr? {
    return const.boolLiteral?.let {
        when (op.operatorType) {
            AND ->
                when (it.text) {
                    "true" -> expr
                    "false" -> createPsiElement(project, "false")
                    else -> null
                }
            OR ->
                when (it.text) {
                    "true" -> createPsiElement(project, "true")
                    "false" -> expr
                    else -> null
                }
            else ->
                null
        }
    }
}

/**
 * Evaluates a boolean expression if can.
 *
 * @return result of evaluation or `null` if can't simplify or
 *         if it is not a boolean expression.
 */
fun RsExpr.evalBooleanExpression(): Boolean? {
    return when (this) {
        is RsLitExpr ->
            (kind as? RsLiteralKind.Boolean)?.value

        is RsBinaryExpr -> when (operatorType) {
            AND -> {
                val lhs = left.evalBooleanExpression() ?: return null
                if (!lhs) return false
                val rhs = right?.evalBooleanExpression() ?: return null
                lhs && rhs
            }
            OR -> {
                val lhs = left.evalBooleanExpression() ?: return null
                if (lhs) return true
                val rhs = right?.evalBooleanExpression() ?: return null
                lhs || rhs
            }
            ArithmeticOp.BIT_XOR -> {
                val lhs = left.evalBooleanExpression() ?: return null
                val rhs = right?.evalBooleanExpression() ?: return null
                lhs xor rhs
            }
            else -> null
        }

        is RsUnaryExpr -> when (operatorType) {
            UnaryOperator.NOT -> expr?.evalBooleanExpression()?.let { !it }
            else -> null
        }

        is RsParenExpr -> expr.evalBooleanExpression()

        else -> null
    }
}

private fun createPsiElement(project: Project, value: Any) = RsPsiFactory(project).createExpression(value.toString())
