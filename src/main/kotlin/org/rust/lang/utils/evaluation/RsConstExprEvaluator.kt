/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.type

object RsConstExprEvaluator {

    private const val MAX_EXPR_DEPTH: Int = 64

    private val defaultExprPathResolver: (RsPathExpr) -> RsElement? = { it.path.reference.resolve() }

    fun evaluate(
        expr: RsExpr,
        expectedTy: Ty = expr.type,
        pathExprResolver: ((RsPathExpr) -> RsElement?) = defaultExprPathResolver
    ): ExprValue? {
        return when (expectedTy) {
            is TyInteger -> evaluateInteger(expr, expectedTy, pathExprResolver)
            else -> {
                val kind = (expr as? RsLitExpr)?.kind
                when (kind) {
                    is RsLiteralKind.Boolean -> ExprValue.Bool(kind.value)
                    is RsLiteralKind.Float -> ExprValue.Float(kind.value ?: return null)
                    is RsLiteralKind.String -> ExprValue.Str(kind.value ?: return null)
                    is RsLiteralKind.Char -> ExprValue.Char(kind.value ?: return null)
                    else -> null
                }
            }
        }
    }

    private fun evaluateInteger(
        expr: RsExpr,
        expectedTy: TyInteger,
        pathExprResolver: ((RsPathExpr) -> RsElement?)
    ): ExprValue.Integer? {

        fun RsLitExpr.eval(expectedTy: TyInteger): Long? {
            val textValue = integerLiteralValue?.removeSuffix(expectedTy.name) ?: return null
            val (start, radix) = when (textValue.take(2)) {
                "0x" -> 2 to 16
                "0o" -> 2 to 8
                "0b" -> 2 to 2
                else -> 0 to 10
            }
            val cleanTextValue = textValue.substring(start).filter { it != '_' }
            return try {
                java.lang.Long.parseLong(cleanTextValue, radix).validValueOrNull(expectedTy)
            } catch (e: NumberFormatException) {
                null
            }
        }

        fun eval(expr: RsExpr?, depth: Int): Long? {
            // To prevent SO we restrict max depth of expression
            if (depth >= MAX_EXPR_DEPTH) return null
            return when (expr) {
                is RsLitExpr -> expr.eval(expectedTy)
                is RsPathExpr -> {
                    val const = pathExprResolver(expr) as? RsConstant ?: return null
                    if (!const.isConst) return null
                    val path = (const.typeReference?.typeElement as? RsBaseType)?.path ?: return null
                    val integerType = TyPrimitive.fromPath(path) as? TyInteger ?: return null
                    if (integerType == expectedTy) eval(const.expr, depth + 1) else null
                }
                is RsParenExpr -> eval(expr.expr, depth + 1)
                is RsUnaryExpr -> {
                    if (expr.operatorType != UnaryOperator.MINUS) return null
                    val value = eval(expr.expr, depth + 1) ?: return null
                    (-value).validValueOrNull(expectedTy)
                }
                is RsBinaryExpr -> {
                    val op = expr.operatorType as? ArithmeticOp ?: return null
                    val leftValue = eval(expr.left, depth + 1) ?: return null
                    val rightValue = eval(expr.right, depth + 1) ?: return null
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
                else -> null
            }
        }

        return eval(expr, 0)?.let(ExprValue::Integer)
    }
}

// It returns wrong values for large types like `i128` or `usize`
// But looks like like it's enough for real cases
private val TyInteger.validValuesRange: LongRange get() = when (this) {
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
