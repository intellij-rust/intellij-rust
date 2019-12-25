/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.evaluation

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

fun ConstExpr<*>.toConst(): Const =
    when (this) {
        is ConstExpr.Constant -> const
        is ConstExpr.Value -> CtValue(this)
        is ConstExpr.Error -> CtUnknown
        else -> CtUnevaluated(this)
    }

sealed class ConstExpr<T : Ty>(val flags: TypeFlags = 0) : TypeFoldable<ConstExpr<T>> {
    abstract val expectedTy: T?

    data class Unary<T : Ty>(
        val operator: UnaryOperator,
        val expr: ConstExpr<T>,
        override val expectedTy: T
    ) : ConstExpr<T>(expr.flags) {
        override fun superFoldWith(folder: TypeFolder): Unary<T> = Unary(operator, expr.foldWith(folder), expectedTy)
        override fun superVisitWith(visitor: TypeVisitor): Boolean = expr.visitWith(visitor)
    }

    data class Binary<T : Ty>(
        val left: ConstExpr<T>,
        val operator: BinaryOperator,
        val right: ConstExpr<T>,
        override val expectedTy: T
    ) : ConstExpr<T>(left.flags or right.flags) {
        override fun superFoldWith(folder: TypeFolder): Binary<T> =
            Binary(left.foldWith(folder), operator, right.foldWith(folder), expectedTy)

        override fun superVisitWith(visitor: TypeVisitor): Boolean = left.visitWith(visitor) || right.visitWith(visitor)
    }

    data class Constant<T : Ty>(
        val const: Const,
        override val expectedTy: T
    ) : ConstExpr<T>(const.flags) {
        override fun superFoldWith(folder: TypeFolder): Constant<T> = Constant(const.foldWith(folder), expectedTy)
        override fun superVisitWith(visitor: TypeVisitor): Boolean = const.visitWith(visitor)
    }

    sealed class Value<T : Ty> : ConstExpr<T>() {
        override fun superFoldWith(folder: TypeFolder): Value<T> = this
        override fun superVisitWith(visitor: TypeVisitor): Boolean = false

        data class Bool(val value: Boolean) : Value<TyBool>() {
            override val expectedTy: TyBool = TyBool
            override fun toString(): String = value.toString()
        }

        data class Integer(val value: Long, override val expectedTy: TyInteger) : Value<TyInteger>() {
            override fun toString(): String = value.toString()
        }

        data class Float(val value: Double, override val expectedTy: TyFloat) : Value<TyFloat>() {
            override fun toString(): String = value.toString()
        }

        data class Char(val value: String) : Value<TyChar>() {
            override val expectedTy: TyChar = TyChar
            override fun toString(): String = value
        }

        data class Str(val value: String, override val expectedTy: TyReference) : Value<TyReference>() {
            override fun toString(): String = value
        }
    }

    class Error<T : Ty> : ConstExpr<T>() {
        override val expectedTy: T? = null
        override fun superFoldWith(folder: TypeFolder): ConstExpr<T> = this
        override fun superVisitWith(visitor: TypeVisitor): Boolean = false
    }
}
