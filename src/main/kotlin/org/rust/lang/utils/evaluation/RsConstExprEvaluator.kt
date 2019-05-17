/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

private val STR_REF_TYPE: TyReference = TyReference(TyStr, Mutability.IMMUTABLE)

object RsConstExprEvaluator {

    private val defaultExprPathResolver: (RsPathExpr) -> RsElement? = { it.path.reference.resolve() }

    fun evaluate(
        expr: RsExpr,
        expectedTy: Ty = expr.type,
        pathExprResolver: ((RsPathExpr) -> RsElement?)? = defaultExprPathResolver
    ): ExprValue? {
        val evaluation = when (expectedTy) {
            is TyInteger -> IntegerExprEvaluation(expectedTy, pathExprResolver)
            is TyBool -> BoolExprEvaluation(pathExprResolver)
            is TyFloat -> FloatExprEvaluation(expectedTy, pathExprResolver)
            is TyChar -> CharExprEvaluation(pathExprResolver)
            // TODO: type should be "wider"
            STR_REF_TYPE -> StrExprEvaluation(pathExprResolver)
            else -> null
        }
        return evaluation?.evaluate(expr)
    }
}

private open class ExprEvaluation<T: Ty, V>(
    protected val expectedTy: T,
    private val pathExprResolver: ((RsPathExpr) -> RsElement?)?,
    private val evalLitExpr: RsLitExpr.() -> V?,
    private val exprValueCtr: (V) -> ExprValue
) {

    fun evaluate(expr: RsExpr?): ExprValue? = evaluate(expr, 0)?.let(exprValueCtr)

    protected fun evaluate(expr: RsExpr?, depth: Int): V? {
        // To prevent SO we restrict max depth of expression
        if (depth >= MAX_EXPR_DEPTH) return null
        return evaluateInner(expr, depth)
    }

    protected open fun evaluateInner(expr: RsExpr?, depth: Int): V? {
        return when (expr) {
            is RsLitExpr -> expr.evalLitExpr()
            is RsParenExpr -> evaluate(expr.expr, depth + 1)
            is RsPathExpr -> {
                val const = pathExprResolver?.invoke(expr) as? RsConstant ?: return null
                if (!const.isConst) return null
                val path = (const.typeReference?.typeElement as? RsBaseType)?.path ?: return null
                if (TyPrimitive.fromPath(path) != expectedTy) return null
                evaluate(const.expr, depth + 1)
            }
            else -> null
        }
    }

    companion object {
        private const val MAX_EXPR_DEPTH: Int = 64
    }
}

private class IntegerExprEvaluation(
    expectedTy: TyInteger,
    pathExprResolver: ((RsPathExpr) -> RsElement?)?
) : ExprEvaluation<TyInteger, Long>(expectedTy, pathExprResolver, RsLitExpr::integerValue, ExprValue::Integer) {

    override fun evaluateInner(expr: RsExpr?, depth: Int): Long? {
        return when (expr) {
            is RsUnaryExpr -> {
                if (expr.operatorType != UnaryOperator.MINUS) return null
                val value = evaluate(expr.expr, depth + 1) ?: return null
                (-value).validValueOrNull(expectedTy)
            }
            is RsBinaryExpr -> {
                val op = expr.operatorType as? ArithmeticOp ?: return null
                val leftValue = evaluate(expr.left, depth + 1) ?: return null
                val rightValue = evaluate(expr.right, depth + 1) ?: return null
                // TODO: check overflow
                val result = when (op) {
                    ArithmeticOp.ADD -> leftValue + rightValue
                    ArithmeticOp.SUB -> leftValue - rightValue
                    ArithmeticOp.MUL -> leftValue * rightValue
                    ArithmeticOp.DIV -> if (rightValue == 0L) null else leftValue / rightValue
                    ArithmeticOp.REM -> if (rightValue == 0L) null else leftValue % rightValue
                    ArithmeticOp.BIT_AND -> leftValue and rightValue
                    ArithmeticOp.BIT_OR -> leftValue or rightValue
                    ArithmeticOp.BIT_XOR -> leftValue xor rightValue
                    // We can't simply convert `rightValue` to Int
                    // because after conversion of quite large Long values (> 2^31 - 1)
                    // we can get any Int value including negative one
                    // so it can lead to incorrect result.
                    // But if `rightValue` >= `java.lang.Long.BYTES`
                    // we know result without computation:
                    // overflow in 'shl' case and 0 in 'shr' case.
                    ArithmeticOp.SHL -> if (rightValue >= java.lang.Long.BYTES) null else leftValue shl rightValue.toInt()
                    ArithmeticOp.SHR -> if (rightValue >= java.lang.Long.BYTES) 0 else leftValue shr rightValue.toInt()
                }
                result?.validValueOrNull(expectedTy)
            }
            else -> super.evaluateInner(expr, depth)
        }
    }

    // It returns wrong values for large types like `i128` or `usize`
    // But looks like like it's enough for real cases
    private val TyInteger.validValuesRange: LongRange
        get() = when (this) {
            TyInteger.U8 -> LongRange(0, 1L shl 8)
            TyInteger.U16 -> LongRange(0, 1L shl 16)
            TyInteger.U32 -> LongRange(0, 1L shl 32)
            TyInteger.U64 -> LongRange(0, Long.MAX_VALUE)
            TyInteger.U128 -> LongRange(0, Long.MAX_VALUE)
            TyInteger.USize -> LongRange(0, Long.MAX_VALUE)
            TyInteger.I8 -> LongRange(-(1L shl 7), (1L shl 7) - 1)
            TyInteger.I16 -> LongRange(-(1L shl 15), (1L shl 15) - 1)
            TyInteger.I32 -> LongRange(-(1L shl 31), (1L shl 31) - 1)
            TyInteger.I64 -> LongRange(Long.MIN_VALUE, Long.MAX_VALUE)
            TyInteger.I128 -> LongRange(Long.MIN_VALUE, Long.MAX_VALUE)
            TyInteger.ISize -> LongRange(Long.MIN_VALUE, Long.MAX_VALUE)
        }

    private fun Long.validValueOrNull(ty: TyInteger): Long? = if (this in ty.validValuesRange) this else null
}

private class BoolExprEvaluation(
    pathExprResolver: ((RsPathExpr) -> RsElement?)?
) : ExprEvaluation<TyBool, Boolean>(TyBool, pathExprResolver, RsLitExpr::booleanValue, ExprValue::Bool) {

    override fun evaluateInner(expr: RsExpr?, depth: Int): Boolean? {
        return when (expr) {
            is RsBinaryExpr -> when (expr.operatorType) {
                LogicOp.AND -> {
                    val lhs = evaluate(expr.left, depth + 1) ?: return null
                    if (!lhs) return false // false && _ --> false
                    val rhs = evaluate(expr.right, depth + 1) ?: return null
                    lhs && rhs
                }
                LogicOp.OR -> {
                    val lhs = evaluate(expr.left, depth + 1) ?: return null
                    if (lhs) return true // true || _ --> true
                    val rhs = evaluate(expr.right, depth + 1) ?: return null
                    lhs || rhs
                }
                ArithmeticOp.BIT_XOR -> {
                    val lhs = evaluate(expr.left, depth + 1) ?: return null
                    val rhs = evaluate(expr.right, depth + 1) ?: return null
                    lhs xor rhs
                }
                else -> null
            }
            is RsUnaryExpr -> when (expr.operatorType) {
                UnaryOperator.NOT -> evaluate(expr.expr, depth + 1)?.let { !it }
                else -> null
            }
            else -> super.evaluateInner(expr, depth)
        }
    }
}

private class FloatExprEvaluation(
    expectedTy: TyFloat,
    pathExprResolver: ((RsPathExpr) -> RsElement?)?
) : ExprEvaluation<TyFloat, Double>(expectedTy, pathExprResolver, RsLitExpr::floatValue, ExprValue::Float)

private class CharExprEvaluation(
    pathExprResolver: ((RsPathExpr) -> RsElement?)?
) : ExprEvaluation<TyChar, String>(TyChar, pathExprResolver, RsLitExpr::charValue, ExprValue::Char)

private class StrExprEvaluation(
    pathExprResolver: ((RsPathExpr) -> RsElement?)?
) : ExprEvaluation<TyReference, String>(STR_REF_TYPE, pathExprResolver, RsLitExpr::stringValue, ExprValue::Str)
