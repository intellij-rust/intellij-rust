/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun RsExpr.toConstExpr(
    expectedTy: Ty = type,
    resolver: PathExprResolver? = PathExprResolver.default
): ConstExpr<out Ty>? {
    val builder = when (expectedTy) {
        is TyInteger -> IntegerConstExprBuilder(expectedTy, resolver)
        is TyBool -> BoolConstExprBuilder(resolver)
        is TyFloat -> FloatConstExprBuilder(expectedTy, resolver)
        is TyChar -> CharConstExprBuilder(resolver)
        // TODO: type should be "wider"
        STR_REF_TYPE -> StrConstExprBuilder(resolver)
        else -> null
    }
    return builder?.build(this)
}

private val STR_REF_TYPE: TyReference = TyReference(TyStr, Mutability.IMMUTABLE)

private abstract class ConstExprBuilder<T : Ty> {
    protected abstract val expectedTy: T
    protected abstract val resolver: PathExprResolver?

    protected abstract fun wrap(expr: RsLitExpr): ConstExpr<T>?

    protected fun makeLeafParameter(parameter: RsConstParameter): ConstExpr<T> =
        ConstExpr.Constant(CtConstParameter(parameter), expectedTy)

    fun build(expr: RsExpr?): ConstExpr<T>? = build(expr, 0)

    protected fun build(expr: RsExpr?, depth: Int): ConstExpr<T>? {
        // To prevent SO we restrict max depth of expression
        if (depth >= MAX_EXPR_DEPTH) return null
        return buildInner(expr, depth)
    }

    protected open fun buildInner(expr: RsExpr?, depth: Int): ConstExpr<T>? {
        return when (expr) {
            is RsLitExpr -> wrap(expr)
            is RsParenExpr -> build(expr.expr, depth + 1)
            is RsBlockExpr -> build(expr.block.expr, depth + 1)
            is RsPathExpr -> {
                val element = resolver?.invoke(expr)

                val typeReference = when (element) {
                    is RsConstant -> element.typeReference?.takeIf { element.isConst }
                    is RsConstParameter -> element.typeReference
                    else -> null
                }

                val typeElementPath = (typeReference?.skipParens() as? RsBaseType)?.path ?: return null
                if (TyPrimitive.fromPath(typeElementPath) != expectedTy) return null

                when (element) {
                    is RsConstant -> build(element.expr, depth + 1)
                    is RsConstParameter -> makeLeafParameter(element)
                    else -> null
                }
            }
            else -> null
        }
    }

    companion object {
        private const val MAX_EXPR_DEPTH: Int = 64
    }
}

private class IntegerConstExprBuilder(
    override val expectedTy: TyInteger,
    override val resolver: PathExprResolver?
) : ConstExprBuilder<TyInteger>() {

    override fun wrap(expr: RsLitExpr): ConstExpr<TyInteger>? =
        expr.integerValue?.let { ConstExpr.Value.Integer(it, expectedTy) }

    override fun buildInner(expr: RsExpr?, depth: Int): ConstExpr<TyInteger>? {
        return when (expr) {
            is RsUnaryExpr -> {
                if (expr.operatorType != UnaryOperator.MINUS) return null
                val value = build(expr.expr, depth + 1) ?: return null
                ConstExpr.Unary(UnaryOperator.MINUS, value, expectedTy)
            }
            is RsBinaryExpr -> {
                val op = expr.operatorType as? ArithmeticOp ?: return null
                val lhs = build(expr.left, depth + 1) ?: return null
                val rhs = build(expr.right, depth + 1) ?: return null
                ConstExpr.Binary(lhs, op, rhs, expectedTy)
            }
            else -> super.buildInner(expr, depth)
        }
    }
}

private class BoolConstExprBuilder(
    override val resolver: PathExprResolver?
) : ConstExprBuilder<TyBool>() {
    override val expectedTy: TyBool = TyBool

    override fun wrap(expr: RsLitExpr): ConstExpr<TyBool>? = expr.booleanValue?.let { ConstExpr.Value.Bool(it) }

    override fun buildInner(expr: RsExpr?, depth: Int): ConstExpr<TyBool>? {
        return when (expr) {
            is RsBinaryExpr -> when (expr.operatorType) {
                LogicOp.AND -> {
                    val lhs = build(expr.left, depth + 1) ?: return null
                    val rhs = build(expr.right, depth + 1) ?: ConstExpr.Error()
                    ConstExpr.Binary(lhs, LogicOp.AND, rhs, expectedTy)
                }
                LogicOp.OR -> {
                    val lhs = build(expr.left, depth + 1) ?: return null
                    val rhs = build(expr.right, depth + 1) ?: ConstExpr.Error()
                    ConstExpr.Binary(lhs, LogicOp.OR, rhs, expectedTy)
                }
                ArithmeticOp.BIT_XOR -> {
                    val lhs = build(expr.left, depth + 1) ?: return null
                    val rhs = build(expr.right, depth + 1) ?: return null
                    ConstExpr.Binary(lhs, ArithmeticOp.BIT_XOR, rhs, expectedTy)
                }
                else -> null
            }
            is RsUnaryExpr -> when (expr.operatorType) {
                UnaryOperator.NOT -> {
                    val value = build(expr.expr, depth + 1) ?: return null
                    ConstExpr.Unary(UnaryOperator.NOT, value, expectedTy)
                }
                else -> null
            }
            else -> super.buildInner(expr, depth)
        }
    }
}

private class FloatConstExprBuilder(
    override val expectedTy: TyFloat,
    override val resolver: PathExprResolver?
) : ConstExprBuilder<TyFloat>() {
    override fun wrap(expr: RsLitExpr): ConstExpr<TyFloat>? =
        expr.floatValue?.let { ConstExpr.Value.Float(it, expectedTy) }
}

private class CharConstExprBuilder(
    override val resolver: PathExprResolver?
) : ConstExprBuilder<TyChar>() {
    override val expectedTy: TyChar = TyChar
    override fun wrap(expr: RsLitExpr): ConstExpr<TyChar>? = expr.charValue?.let { ConstExpr.Value.Char(it) }
}

private class StrConstExprBuilder(
    override val resolver: PathExprResolver?
) : ConstExprBuilder<TyReference>() {
    override val expectedTy: TyReference = STR_REF_TYPE
    override fun wrap(expr: RsLitExpr): ConstExpr<TyReference>? =
        expr.stringValue?.let { ConstExpr.Value.Str(it, expectedTy) }
}
