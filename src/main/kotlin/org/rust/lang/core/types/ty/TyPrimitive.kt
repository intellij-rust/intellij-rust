/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.psi.PsiElement
import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsArrayExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVariantDiscriminant
import org.rust.lang.core.psi.ext.hasColonColon
import org.rust.lang.core.psi.ext.sizeExpr

/**
 * These are "atomic" ty (not type constructors, singletons).
 *
 * Definition intentionally differs from the reference: we don't treat
 * tuples or arrays as primitive.
 */
abstract class TyPrimitive : Ty() {

    companion object {
        fun fromPath(path: RsPath): TyPrimitive? {
            if (path.hasColonColon) return null
            val name = path.referenceName

            val integerKind = TyInteger.Kind.values().find { it.name == name }
            if (integerKind != null) return TyInteger(integerKind)

            val floatKind = TyFloat.Kind.values().find { it.name == name }
            if (floatKind != null) return TyFloat(floatKind)

            return when (name) {
                "bool" -> TyBool
                "char" -> TyChar
                "str" -> TyStr
                else -> null
            }
        }
    }
}

object TyBool : TyPrimitive() {
    override fun toString(): String = tyToString(this)
}

object TyChar : TyPrimitive() {
    override fun toString(): String = tyToString(this)
}

object TyUnit : TyPrimitive() {
    override fun toString(): String = tyToString(this)
}

/** The `!` type. E.g. `unimplemented!()` */
object TyNever : TyPrimitive() {
    override fun toString(): String = tyToString(this)
}

object TyStr : TyPrimitive() {
    override fun toString(): String = tyToString(this)
}

abstract class TyNumeric : TyPrimitive()

class TyInteger(val kind: Kind) : TyNumeric() {
    companion object {
        fun fromSuffixedLiteral(literal: PsiElement): TyInteger? =
            Kind.values().find { literal.text.endsWith(it.name) }?.let(::TyInteger)

        fun fromLiteral(literal: PsiElement): TyInteger {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }
                ?: inferKind(literal)

            return TyInteger(kind ?: DEFAULT_KIND)
        }

        /**
         * Tries to infer the kind of an unsuffixed integer literal by its context.
         * Fall back to the default kind if can't infer.
         */
        private fun inferKind(literal: PsiElement): Kind? {
            val expr = literal.parent as? RsLitExpr ?: return null
            return when {
                expr.isArraySize -> Kind.usize
                expr.isEnumVariantDiscriminant -> Kind.isize
                else -> null
            }
        }

        private val RsLitExpr.isArraySize: Boolean get() = (parent as? RsArrayExpr)?.sizeExpr == this
        private val RsLitExpr.isEnumVariantDiscriminant: Boolean get() = parent is RsVariantDiscriminant

        val DEFAULT_KIND = Kind.i32
    }

    enum class Kind {
        u8, u16, u32, u64, u128, usize,
        i8, i16, i32, i64, i128, isize
    }

    // Ignore `isKindWeak` for the purposes of equality
    override fun equals(other: Any?): Boolean = other is TyInteger && other.kind == kind
    override fun hashCode(): Int = kind.hashCode()

    override fun toString(): String = tyToString(this)
}

class TyFloat(val kind: Kind) : TyNumeric() {
    companion object {
        fun fromSuffixedLiteral(literal: PsiElement): TyFloat? =
            Kind.values().find { literal.text.endsWith(it.name) }?.let(::TyFloat)

        fun fromLiteral(literal: PsiElement): TyFloat {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }

            return TyFloat(kind ?: DEFAULT_KIND)
        }

        val DEFAULT_KIND = Kind.f64
    }

    enum class Kind { f32, f64 }

    // Ignore `isKindWeak` for the purposes of equality
    override fun equals(other: Any?): Boolean = other is TyFloat && other.kind == kind
    override fun hashCode(): Int = kind.hashCode()

    override fun toString(): String = tyToString(this)
}
