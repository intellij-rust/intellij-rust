/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.hasColonColon
import org.rust.lang.core.psi.ext.sizeExpr

/**
 * These are "atomic" ty (not type constructors, singletons).
 *
 * Definition intentionally differs from the reference: we don't treat
 * tuples or arrays as primitive.
 */
interface TyPrimitive : Ty {
    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean =
        this == other

    companion object {
        fun fromPath(path: RsPath): TyPrimitive? {
            if (path.hasColonColon) return null
            if (path.parent !is RsBaseType) return null
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

object TyBool : TyPrimitive {
    override fun toString(): String = "bool"
}

object TyChar : TyPrimitive {
    override fun toString(): String = "char"
}

object TyUnit : TyPrimitive {
    override fun toString(): String = "()"
}

object TyStr : TyPrimitive {
    override fun toString(): String = "str"
}

interface TyNumeric : TyPrimitive {
    val isKindWeak: Boolean

    override fun canUnifyWith(other: Ty, project: Project, mapping: MutableMap<TyTypeParameter, Ty>?): Boolean
        = this == other || javaClass == other.javaClass && (other as TyNumeric).isKindWeak
}

class TyInteger(val kind: Kind, override val isKindWeak: Boolean = false) : TyNumeric {
    companion object {
        fun fromLiteral(literal: PsiElement): TyInteger {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }
                ?: inferKind(literal)

            return TyInteger(kind ?: DEFAULT_KIND, kind == null)
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

    override fun toString(): String = kind.toString()
}

class TyFloat(val kind: Kind, override val isKindWeak: Boolean = false) : TyNumeric {
    companion object {
        fun fromLiteral(literal: PsiElement): TyFloat {
            val kind = Kind.values().find { literal.text.endsWith(it.name) }

            return TyFloat(kind ?: DEFAULT_KIND, kind == null)
        }

        val DEFAULT_KIND = Kind.f64
    }

    enum class Kind { f32, f64 }

    // Ignore `isKindWeak` for the purposes of equality
    override fun equals(other: Any?): Boolean = other is TyFloat && other.kind == kind
    override fun hashCode(): Int = kind.hashCode()

    override fun toString(): String = kind.toString()
}
