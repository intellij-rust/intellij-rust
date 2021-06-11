/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.BinaryOperator
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.types.TypeFlags
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtUnevaluated
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.ty.*
import java.math.BigInteger

fun ConstExpr<*>.toConst(): Const =
    when (this) {
        is ConstExpr.Constant -> const
        is ConstExpr.Value<*, *> -> CtValue(this)
        is ConstExpr.Error -> CtUnknown
        else -> CtUnevaluated(this)
    }

sealed class ConstExpr<T : Ty>(val flags: TypeFlags = 0) : TypeFoldable<ConstExpr<T>> {
    abstract val expectedTy: T?
    open val element: RsExpr? = null

    data class Unary<T : Ty>(
        val operator: UnaryOperator,
        val expr: ConstExpr<T>,
        override val expectedTy: T,
        override val element: RsExpr?
    ) : ConstExpr<T>(expr.flags) {
        override fun superFoldWith(folder: TypeFolder): Unary<T> =
            Unary(operator, expr.foldWith(folder), expectedTy, element)

        override fun superVisitWith(visitor: TypeVisitor): Boolean = expr.visitWith(visitor)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Unary<*>

            if (operator != other.operator) return false
            if (expr != other.expr) return false
            if (expectedTy != other.expectedTy) return false

            return true
        }

        override fun hashCode(): Int {
            var result = operator.hashCode()
            result = 31 * result + expr.hashCode()
            result = 31 * result + expectedTy.hashCode()
            return result
        }
    }

    data class Binary<T : Ty>(
        val left: ConstExpr<T>,
        val operator: BinaryOperator,
        val right: ConstExpr<T>,
        override val expectedTy: T,
        override val element: RsExpr?
    ) : ConstExpr<T>(left.flags or right.flags) {
        override fun superFoldWith(folder: TypeFolder): Binary<T> =
            Binary(left.foldWith(folder), operator, right.foldWith(folder), expectedTy, element)

        override fun superVisitWith(visitor: TypeVisitor): Boolean = left.visitWith(visitor) || right.visitWith(visitor)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Binary<*>

            if (left != other.left) return false
            if (operator != other.operator) return false
            if (right != other.right) return false
            if (expectedTy != other.expectedTy) return false

            return true
        }

        override fun hashCode(): Int {
            var result = left.hashCode()
            result = 31 * result + operator.hashCode()
            result = 31 * result + right.hashCode()
            result = 31 * result + expectedTy.hashCode()
            return result
        }
    }

    data class Constant<T : Ty>(
        val const: Const,
        override val expectedTy: T,
        override val element: RsExpr?
    ) : ConstExpr<T>(const.flags) {
        override fun superFoldWith(folder: TypeFolder): Constant<T> =
            Constant(const.foldWith(folder), expectedTy, element)

        override fun superVisitWith(visitor: TypeVisitor): Boolean = const.visitWith(visitor)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Constant<*>

            if (const != other.const) return false
            if (expectedTy != other.expectedTy) return false

            return true
        }

        override fun hashCode(): Int {
            var result = const.hashCode()
            result = 31 * result + expectedTy.hashCode()
            return result
        }


    }

    sealed class Value<T : Ty, V> : ConstExpr<T>() {
        override fun superFoldWith(folder: TypeFolder): Value<T, V> = this
        override fun superVisitWith(visitor: TypeVisitor): Boolean = false

        abstract val value: V

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Value<*, *>

            if (value != other.value) return false
            if (expectedTy != other.expectedTy) return false

            return true
        }

        override fun hashCode(): Int {
            var result = value.hashCode()
            result = 31 * result + expectedTy.hashCode()
            return result
        }


        data class Bool(override val value: Boolean, override val element: RsExpr?) : Value<TyBool, Boolean>() {
            override val expectedTy: TyBool = TyBool
            override fun toString(): String = value.toString()
        }

        data class Integer(
            override val value: BigInteger,
            override val expectedTy: TyInteger,
            override val element: RsExpr?
        ) : Value<TyInteger, BigInteger>() {
            override fun toString(): String = value.toString()
        }

        data class Float(
            override val value: Double,
            override val expectedTy: TyFloat,
            override val element: RsExpr?
        ) : Value<TyFloat, Double>() {
            override fun toString(): String = value.toString()
        }

        data class Char(override val value: String, override val element: RsExpr?) : Value<TyChar, String>() {
            override val expectedTy: TyChar = TyChar
            override fun toString(): String = value
        }

        data class Str(
            override val value: String,
            override val expectedTy: TyReference,
            override val element: RsExpr?
        ) : Value<TyReference, String>() {
            override fun toString(): String = value
        }
    }

    class Error<T : Ty> : ConstExpr<T>() {
        override val expectedTy: T? = null
        override fun superFoldWith(folder: TypeFolder): ConstExpr<T> = this
        override fun superVisitWith(visitor: TypeVisitor): Boolean = false
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Error<*>

            if (expectedTy != other.expectedTy) return false

            return true
        }

        override fun hashCode(): Int {
            return expectedTy?.hashCode() ?: 0
        }


    }
}
