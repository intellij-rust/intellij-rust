/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.psi.PsiElement
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

            TyInteger.VALUES.find { it.name == name }
                ?.let { return it }
            TyFloat.VALUES.find { it.name == name }
                ?.let { return it }

            return when (name) {
                "bool" -> TyBool
                "char" -> TyChar
                "str" -> TyStr
                else -> null
            }
        }
    }
}

object TyBool : TyPrimitive()

object TyChar : TyPrimitive()

object TyUnit : TyPrimitive()

/** The `!` type. E.g. `unimplemented!()` */
object TyNever : TyPrimitive()

object TyStr : TyPrimitive()

abstract class TyNumeric : TyPrimitive()

sealed class TyInteger(val name: String, val ordinal: Int) : TyNumeric() {
    companion object {
        val DEFAULT = TyInteger.I32
        val VALUES = listOf(U8, U16, U32, U64, U128, USize, I8, I16, I32, I64, I128, ISize)

        fun fromSuffixedLiteral(literal: PsiElement): TyInteger? {
            val text = literal.text
            return VALUES.find { text.endsWith(it.name) }
        }
    }

    object U8: TyInteger("u8", 0)
    object U16: TyInteger("u16", 1)
    object U32: TyInteger("u32", 2)
    object U64: TyInteger("u64", 3)
    object U128: TyInteger("u128", 4)
    object USize : TyInteger("usize", 5)

    object I8: TyInteger("i8", 6)
    object I16: TyInteger("i16", 7)
    object I32: TyInteger("i32", 8)
    object I64: TyInteger("i64", 9)
    object I128: TyInteger("i128", 10)
    object ISize: TyInteger("isize", 11)
}

sealed class TyFloat(val name: String, val ordinal: Int) : TyNumeric() {
    companion object {
        val DEFAULT = TyFloat.F64
        val VALUES = listOf(F32, F64)

        fun fromSuffixedLiteral(literal: PsiElement): TyFloat? {
            val text = literal.text
            return VALUES.find { text.endsWith(it.name) }
        }
    }

    object F32: TyFloat("f32", 0)
    object F64: TyFloat("f64", 1)
}
