/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyPrimitive

val RsArrayType.isSlice: Boolean get() = stub?.isSlice ?: (expr == null)

val RsArrayType.arraySize: Long? get() = calculateArraySize(expr)

private const val MAX_EXPR_DEPTH: Int = 64

private val defaultExprPathResolver: (RsPathExpr) -> RsElement? = { it.path.reference.resolve() }

fun calculateArraySize(expr: RsExpr?, pathExprResolver: ((RsPathExpr) -> RsElement?) = defaultExprPathResolver): Long? {

    fun eval(expr: RsExpr?, depth: Int): Long? {
        // To prevent SO we restrict max depth of expression
        if (depth >= MAX_EXPR_DEPTH) return null
        return when (expr) {
            is RsLitExpr -> expr.integerLiteralValue
                ?.removeSuffix(TyInteger.Kind.usize.name)
                ?.toLongOrNull()
                ?.notNegativeOrNull()
            is RsPathExpr -> {
                val const = pathExprResolver(expr) as? RsConstant ?: return null
                if (!const.isConst) return null
                val path = (const.typeReference?.typeElement as? RsBaseType)?.path ?: return null
                val integerType = TyPrimitive.fromPath(path) as? TyInteger ?: return null
                if (integerType.kind == TyInteger.Kind.usize) eval(const.expr, depth + 1) else null
            }
            is RsParenExpr -> eval(expr.expr, depth + 1)
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
                result?.notNegativeOrNull()
            }
            else -> null
        }
    }

    return eval(expr, 0)
}

private fun Long.notNegativeOrNull(): Long? = if (this < 0) null else this
