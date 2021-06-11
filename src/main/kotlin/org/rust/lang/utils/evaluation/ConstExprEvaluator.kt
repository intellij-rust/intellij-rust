/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import com.intellij.util.containers.addIfNotNull
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.psi.ext.LogicOp
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtUnevaluated
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.needsEval
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.type
import org.rust.lang.utils.evaluation.ConstEvaluationDiagnostic.*
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO

fun RsExpr.evaluate(
    expectedTy: Ty = type,
    resolver: PathExprResolver? = PathExprResolver.default,
    collectDiagnostics: Boolean = false
): ConstEvaluationResult {
    val context = if (collectDiagnostics) ConstEvaluationContext() else null
    val const = toConstExpr(expectedTy, resolver)?.evaluate(context)?.toConst() ?: CtUnknown
    return ConstEvaluationResult(const, context?.diagnostics.orEmpty())
}

class ConstEvaluationContext {
    val diagnostics: MutableList<ConstEvaluationDiagnostic> = mutableListOf()
}

data class ConstEvaluationResult(
    val value: Const,
    val diagnostics: List<ConstEvaluationDiagnostic>
)

sealed class ConstEvaluationDiagnostic(val expr: RsExpr) {
    class UnsupportedUnaryMinus(expr: RsExpr) : ConstEvaluationDiagnostic(expr)
    class IntegerOverflow(expr: RsExpr) : ConstEvaluationDiagnostic(expr)
    class DivisionByZero(expr: RsExpr) : ConstEvaluationDiagnostic(expr)
}

private fun <T : Ty> ConstExpr<T>.evaluate(context: ConstEvaluationContext? = null): ConstExpr<T> =
    when (expectedTy) {
        is TyBool -> simplifyToBool(context)
        is TyInteger -> simplifyToInteger(context)
        else -> this
    }

fun <T> TypeFoldable<T>.tryEvaluate(): T =
    foldWith(object : TypeFolder {
        override fun foldTy(ty: Ty): Ty =
            if (ty.needsEval) ty.superFoldWith(this) else ty

        override fun foldConst(const: Const): Const =
            if (const is CtUnevaluated && const.needsEval) {
                const.expr.evaluate().toConst()
            } else {
                const
            }
    })

private fun <T : Ty> ConstExpr<T>.simplifyToBool(context: ConstEvaluationContext?): ConstExpr<T> {
    val value = when (this) {
        is ConstExpr.Constant -> {
            val const = const as? CtValue
            val value = const?.expr as? ConstExpr.Value.Bool
            value?.value ?: return this
        }
        is ConstExpr.Unary -> {
            if (operator != UnaryOperator.NOT) return ConstExpr.Error()
            when (val result = expr.simplifyToBool(context)) {
                is ConstExpr.Value.Bool -> !result.value
                is ConstExpr.Error -> return ConstExpr.Error()
                else -> return copy(expr = result)
            }
        }
        is ConstExpr.Binary -> {
            val left = left.simplifyToBool(context)
            val right = right.simplifyToBool(context)
            when (operator) {
                LogicOp.AND ->
                    when {
                        left is ConstExpr.Value.Bool && !left.value ->
                            false // false && _ --> false
                        right is ConstExpr.Value.Bool && !right.value ->
                            false // _ && false --> false
                        left is ConstExpr.Value.Bool && right is ConstExpr.Value.Bool ->
                            left.value && right.value
                        left is ConstExpr.Error || right is ConstExpr.Error ->
                            return ConstExpr.Error()
                        else ->
                            return copy(left = left, right = right)
                    }
                LogicOp.OR ->
                    when {
                        left is ConstExpr.Value.Bool && left.value ->
                            true // true || _ --> true
                        right is ConstExpr.Value.Bool && right.value ->
                            true // _ || true --> true
                        left is ConstExpr.Value.Bool && right is ConstExpr.Value.Bool ->
                            left.value || right.value
                        left is ConstExpr.Error || right is ConstExpr.Error ->
                            return ConstExpr.Error()
                        else ->
                            return copy(left = left, right = right)
                    }
                ArithmeticOp.BIT_XOR ->
                    when {
                        left is ConstExpr.Value.Bool && right is ConstExpr.Value.Bool ->
                            left.value xor right.value
                        left is ConstExpr.Error || right is ConstExpr.Error ->
                            return ConstExpr.Error()
                        else ->
                            return copy(left = left, right = right)
                    }
                else ->
                    return ConstExpr.Error()
            }
        }
        else -> return this
    }
    @Suppress("UNCHECKED_CAST")
    return ConstExpr.Value.Bool(value, element) as ConstExpr<T>
}

private val _128: BigInteger = BigInteger.valueOf(128)

private fun <T : Ty> ConstExpr<T>.simplifyToInteger(context: ConstEvaluationContext?): ConstExpr<T> {
    val expectedTy = expectedTy
    if (expectedTy !is TyInteger) return ConstExpr.Error()

    val value = when (this) {
        is ConstExpr.Value.Integer -> value
        is ConstExpr.Constant -> {
            val const = const as? CtValue
            val value = const?.expr as? ConstExpr.Value.Integer
            value?.value ?: return this
        }
        is ConstExpr.Unary -> {
            if (operator != UnaryOperator.MINUS) return ConstExpr.Error()
            if (!expectedTy.signed) {
                context?.diagnostics?.addIfNotNull(element?.let(::UnsupportedUnaryMinus))
                return ConstExpr.Error()
            }
            if (expr is ConstExpr.Value.Integer) {
                -expr.value
            } else {
                when (val result = expr.simplifyToInteger(context)) {
                    is ConstExpr.Value.Integer -> -result.value
                    is ConstExpr.Error -> return ConstExpr.Error()
                    else -> return copy(expr = result)
                }
            }
        }
        is ConstExpr.Binary -> {
            val left = left.simplifyToInteger(context)
            val right = right.simplifyToInteger(context)
            when {
                left is ConstExpr.Value.Integer && right is ConstExpr.Value.Integer ->
                    when (operator) {
                        ArithmeticOp.ADD -> left.value + right.value
                        ArithmeticOp.SUB -> left.value - right.value
                        ArithmeticOp.MUL -> left.value * right.value
                        ArithmeticOp.DIV -> {
                            if (right.value == ZERO) {
                                context?.diagnostics?.addIfNotNull(element?.let(::DivisionByZero))
                                null
                            } else {
                                left.value / right.value
                            }
                        }
                        ArithmeticOp.REM -> {
                            if (right.value == ZERO) {
                                context?.diagnostics?.addIfNotNull(element?.let(::DivisionByZero))
                                null
                            } else {
                                left.value % right.value
                            }
                        }
                        ArithmeticOp.BIT_AND -> left.value and right.value
                        ArithmeticOp.BIT_OR -> left.value or right.value
                        ArithmeticOp.BIT_XOR -> left.value xor right.value
                        ArithmeticOp.SHL -> {
                            if (right.value >= _128) {
                                context?.diagnostics?.addIfNotNull(element?.let(::IntegerOverflow))
                                null
                            } else {
                                left.value shl right.value.toInt()
                            }
                        }
                        ArithmeticOp.SHR -> {
                            if (right.value >= _128) {
                                ZERO
                            } else {
                                left.value shr right.value.toInt()
                            }
                        }
                        else -> return ConstExpr.Error()
                    }
                left is ConstExpr.Error || right is ConstExpr.Error ->
                    return ConstExpr.Error()
                else ->
                    return copy(left = left, right = right)
            }
        }
        else -> return this
    } ?: return ConstExpr.Error()

    if (value !in expectedTy.validValuesRange) {
        context?.diagnostics?.addIfNotNull(element?.let(::IntegerOverflow))
        return ConstExpr.Error()
    }

    @Suppress("UNCHECKED_CAST")
    return ConstExpr.Value.Integer(value, expectedTy, element) as ConstExpr<T>
}

private val TyInteger.validValuesRange: IntegerRange
    get() = when (this) {
        TyInteger.U8 -> IntegerRange(ZERO, (ONE shl 8) - ONE)
        TyInteger.U16 -> IntegerRange(ZERO, (ONE shl 16) - ONE)
        TyInteger.U32 -> IntegerRange(ZERO, (ONE shl 32) - ONE)
        TyInteger.U64 -> IntegerRange(ZERO, (ONE shl 64) - ONE)
        TyInteger.U128 -> IntegerRange(ZERO, (ONE shl 128) - ONE)
        // FIXME: `target_pointer_width` should be taken into account
        TyInteger.USize -> IntegerRange(ZERO, (ONE shl 64) - ONE)
        TyInteger.I8 -> IntegerRange(-(ONE shl 7), (ONE shl 7) - ONE)
        TyInteger.I16 -> IntegerRange(-(ONE shl 15), (ONE shl 15) - ONE)
        TyInteger.I32 -> IntegerRange(-(ONE shl 31), (ONE shl 31) - ONE)
        TyInteger.I64 -> IntegerRange(-(ONE shl 63), (ONE shl 63) - ONE)
        TyInteger.I128 -> IntegerRange(-(ONE shl 127), (ONE shl 127) - ONE)
        // FIXME: `target_pointer_width` should be taken into account
        TyInteger.ISize -> IntegerRange(-(ONE shl 63), (ONE shl 63) - ONE)
    }

class IntegerRange(override val start: BigInteger, override val endInclusive: BigInteger) : ClosedRange<BigInteger>
