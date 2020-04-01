/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.LogicOp
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.psi.ext.unwrapParenExprs

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
        is RsParenExpr -> expr?.isPure() == true
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

/***
 * Go to the RsExpr, which parent is not RsParenExpr.
 *
 * @return RsExpr, which parent is not RsParenExpr.
 */
fun RsExpr.skipParenExprUp(): RsExpr {
    var element = this
    var parent = element.parent
    while (parent is RsParenExpr) {
        element = parent
        parent = parent.parent
    }

    return element
}

/***
 * Go down to the item below RsParenExpr.
 *
 * @return a child expression without parentheses.
 */
fun RsCondition.skipParenExprDown(): RsExpr =
    unwrapParenExprs(expr)
