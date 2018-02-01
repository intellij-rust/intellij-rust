/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.psi.PsiElement
import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.hasColonColon

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

data class TyInteger(val kind: Kind) : TyNumeric() {
    companion object {
        val DEFAULT_KIND = Kind.i32
    }

    enum class Kind {
        u8, u16, u32, u64, u128, usize,
        i8, i16, i32, i64, i128, isize;

        companion object {
            fun fromSuffixedLiteral(literal: PsiElement): Kind? =
                Kind.values().find { literal.text.endsWith(it.name) }
        }
    }

    override fun toString(): String = tyToString(this)
}

data class TyFloat(val kind: Kind) : TyNumeric() {
    companion object {
        val DEFAULT_KIND = Kind.f64
    }

    enum class Kind {
        f32, f64;

        companion object {
            fun fromSuffixedLiteral(literal: PsiElement): Kind? =
                Kind.values().find { literal.text.endsWith(it.name) }
        }
    }

    override fun toString(): String = tyToString(this)
}
