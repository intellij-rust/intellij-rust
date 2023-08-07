/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.lang.core.psi.RsConstParameter
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.*
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.needsEval
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

fun RsExpr.evaluate(
    expectedTy: Ty = type,
    resolver: PathExprResolver? = PathExprResolver.default
): Const = toConstExpr(expectedTy, resolver)?.evaluate()?.toConst() ?: CtUnknown

fun RsElement.toConst(
    expectedTy: Ty,
    resolver: PathExprResolver? = PathExprResolver.default
): Const = when (this) {
    is RsExpr -> evaluate(expectedTy, resolver)
    is RsPathType -> when (val resolved = path.reference?.resolve()) {
        is RsConstParameter -> CtConstParameter(resolved)
        is RsConstant -> when {
            resolved.isConst -> {
                val type = resolved.typeReference?.normType ?: TyUnknown
                resolved.expr?.evaluate(type, resolver) ?: CtUnknown
            }
            else -> CtUnknown
        }
        else -> CtUnknown
    }
    else -> CtUnknown
}

private fun <T : Ty> ConstExpr<T>.evaluate(): ConstExpr<T> =
    when (expectedTy) {
        is TyBool -> simplifyToBool(this)
        is TyInteger -> simplifyToInteger(this)
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

private fun <T : Ty> simplifyToBool(expr: ConstExpr<T>): ConstExpr<T> {
    val value = when (expr) {
        is ConstExpr.Constant -> {
            val const = expr.const as? CtValue
            val value = const?.expr as? ConstExpr.Value.Bool
            value?.value ?: return expr
        }
        is ConstExpr.Unary -> {
            if (expr.operator != UnaryOperator.NOT) return ConstExpr.Error()
            when (val result = simplifyToBool(expr.expr)) {
                is ConstExpr.Value.Bool -> !result.value
                is ConstExpr.Error -> return ConstExpr.Error()
                else -> return expr.copy(expr = result)
            }
        }
        is ConstExpr.Binary -> {
            val left = simplifyToBool(expr.left)
            val right = simplifyToBool(expr.right)
            when (expr.operator) {
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
                            return expr.copy(left = left, right = right)
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
                            return expr.copy(left = left, right = right)
                    }
                ArithmeticOp.BIT_XOR ->
                    when {
                        left is ConstExpr.Value.Bool && right is ConstExpr.Value.Bool ->
                            left.value xor right.value
                        left is ConstExpr.Error || right is ConstExpr.Error ->
                            return ConstExpr.Error()
                        else ->
                            return expr.copy(left = left, right = right)
                    }
                else ->
                    return ConstExpr.Error()
            }
        }
        else -> return expr
    }
    @Suppress("UNCHECKED_CAST")
    return ConstExpr.Value.Bool(value) as ConstExpr<T>
}

private fun <T : Ty> simplifyToInteger(expr: ConstExpr<T>): ConstExpr<T> {
    val expectedTy = expr.expectedTy
    if (expectedTy !is TyInteger) return ConstExpr.Error()

    val value = when (expr) {
        is ConstExpr.Constant -> {
            val const = expr.const as? CtValue
            val value = const?.expr as? ConstExpr.Value.Integer
            value?.value ?: return expr
        }
        is ConstExpr.Unary -> {
            if (expr.operator != UnaryOperator.MINUS) return ConstExpr.Error()
            when (val result = simplifyToInteger(expr.expr)) {
                is ConstExpr.Value.Integer -> -result.value
                is ConstExpr.Error -> return ConstExpr.Error()
                else -> return expr.copy(expr = result)
            }
        }
        is ConstExpr.Binary -> {
            val left = simplifyToInteger(expr.left)
            val right = simplifyToInteger(expr.right)
            when {
                left is ConstExpr.Value.Integer && right is ConstExpr.Value.Integer ->
                    // TODO: check overflow
                    when (expr.operator) {
                        ArithmeticOp.ADD -> left.value + right.value
                        ArithmeticOp.SUB -> left.value - right.value
                        ArithmeticOp.MUL -> left.value * right.value
                        ArithmeticOp.DIV -> if (right.value == 0L) null else left.value / right.value
                        ArithmeticOp.REM -> if (right.value == 0L) null else left.value % right.value
                        ArithmeticOp.BIT_AND -> left.value and right.value
                        ArithmeticOp.BIT_OR -> left.value or right.value
                        ArithmeticOp.BIT_XOR -> left.value xor right.value
                        // We can't simply convert `right.value` to Int because after conversion of quite large Long values
                        // (> 2^31 - 1) we can get any Int value including negative one, so it can lead to incorrect result.
                        // But if `rightValue` >= `java.lang.Long.BYTES` we know result without computation:
                        // overflow in 'shl' case and 0 in 'shr' case.
                        ArithmeticOp.SHL -> if (right.value >= java.lang.Long.BYTES) null else left.value shl right.value.toInt()
                        ArithmeticOp.SHR -> if (right.value >= java.lang.Long.BYTES) 0 else left.value shr right.value.toInt()
                        else -> return ConstExpr.Error()
                    }
                left is ConstExpr.Error || right is ConstExpr.Error ->
                    return ConstExpr.Error()
                else ->
                    return expr.copy(left = left, right = right)
            }

        }
        else -> return expr
    }
    val checkedValue = value?.validValueOrNull(expectedTy) ?: return ConstExpr.Error()
    @Suppress("UNCHECKED_CAST")
    return ConstExpr.Value.Integer(checkedValue, expectedTy) as ConstExpr<T>
}

private fun Long.validValueOrNull(ty: TyInteger): Long? = takeIf { it in ty.validValuesRange }

// It returns wrong values for large types like `i128` or `usize`, but looks like it's enough for real cases
val TyInteger.validValuesRange: LongRange
    get() = when (this) {
        is TyInteger.U8 -> LongRange(0, (1L shl 8) - 1)
        is TyInteger.U16 -> LongRange(0, (1L shl 16) - 1)
        is TyInteger.U32 -> LongRange(0, (1L shl 32) - 1)
        is TyInteger.U64 -> LongRange(0, Long.MAX_VALUE) // FIXME!
        is TyInteger.U128 -> LongRange(0, Long.MAX_VALUE) // FIXME!
        is TyInteger.USize -> LongRange(0, Long.MAX_VALUE) // FIXME!
        is TyInteger.I8 -> LongRange(-(1L shl 7), (1L shl 7) - 1)
        is TyInteger.I16 -> LongRange(-(1L shl 15), (1L shl 15) - 1)
        is TyInteger.I32 -> LongRange(-(1L shl 31), (1L shl 31) - 1)
        is TyInteger.I64 -> LongRange(1L shl 63, (1L shl 63) - 1)
        is TyInteger.I128 -> LongRange(Long.MIN_VALUE, Long.MAX_VALUE) // FIXME!
        is TyInteger.ISize -> LongRange(1L shl 63, (1L shl 63) - 1)
    }
