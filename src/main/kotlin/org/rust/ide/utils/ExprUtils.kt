package org.rust.ide.utils

import org.rust.lang.core.psi.*

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
            null -> exprList.allMaybe { it.isPure() }
            else -> exprList[0].isPure() // Array literal of form [expr; size],
        // size is a compile-time constant, so it is always pure
        }
        is RsStructExpr -> when (structExprBody.dotdot) {
            null -> structExprBody.structExprFieldList
                .map { it.expr }
                .allMaybe { it?.isPure() } // TODO: Why `it` can be null?
            else -> null // TODO: handle update case (`Point{ y: 0, z: 10, .. base}`)
        }
        is RsTupleExpr -> exprList.allMaybe { it.isPure() }
        is RsFieldExpr -> expr.isPure()
        is RsParenExpr -> expr.isPure()
        is RsBreakExpr -> false // Changes execution flow
        is RsContExpr -> false  // -//-
        is RsRetExpr -> false   // -//-
        is RsTryExpr -> false   // -//-
        is RsPathExpr -> true   // Paths are always pure
        is RsQualPathExpr -> true
        is RsLitExpr -> true
        is RsUnitExpr -> true

        // TODO: more complex analysis of blocks of code and search of implemented traits
        is RsBinaryExpr -> null // Have to search if operation is overloaded
        is RsBlockExpr -> null  // Have to analyze lines, very hard case
        is RsCastExpr -> null;  // `expr.isPure()` maybe not true, think about side-effects, may panic while cast
        is RsCallExpr -> null   // All arguments and function itself must be pure, very hard case
        is RsForExpr -> null    // Always return (), if pure then can be replaced with it
        is RsIfExpr -> null
        is RsIndexExpr -> null  // Index trait can be overloaded, can panic if out of bounds
        is RsLambdaExpr -> null
        is RsLoopExpr -> null
        is RsMacroExpr -> null
        is RsMatchExpr -> null
        is RsMethodCallExpr -> null
        is RsRangeExpr -> null
        is RsUnaryExpr -> null  // May be overloaded
        is RsWhileExpr -> null
        else -> null
    }
}

/**
 * Enum class representing unary operator in rust.
 */
enum class UnaryOperator {
    REF, // `&a`
    REF_MUT, // `&mut a`
    DEREF, // `*a`
    MINUS, // `-a`
    NOT, // `!a`
    BOX, // `box a`
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

