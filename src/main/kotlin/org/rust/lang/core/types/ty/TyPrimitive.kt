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

    abstract val name: String

    companion object {
        fun fromPath(path: RsPath): TyPrimitive? {
            if (path.hasColonColon) return null
            val name = path.referenceName

            TyInteger.fromName(name)?.let { return it }
            TyFloat.fromName(name)?.let { return it }

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
    override val name: String = "bool"
}

object TyChar : TyPrimitive() {
    override val name: String = "char"
}

object TyUnit : TyPrimitive() {
    override val name: String = "unit"
}

/** The `!` type. E.g. `unimplemented!()` */
object TyNever : TyPrimitive() {
    override val name: String = "never"
}

object TyStr : TyPrimitive() {
    override val name: String = "str"
}

abstract class TyNumeric : TyPrimitive()

sealed class TyInteger(override val name: String, val ordinal: Int) : TyNumeric() {

    // This fixes NPE caused by java classes initialization order. Details:
    // Kotlin `object`s compile into java classes with `INSTANCE` static field
    // and `companion object` fields compile into static field of the host class.
    // Our objects (`U8`, `U16` etc) are extend `TyInteger` class.
    // In java, parent classes are initialized first. So, if we accessing,
    // for example, `U8` object first, we really accessing `U8.INSTANCE` field,
    // that requests to initialize `U8` class, that requests to initialize
    // `TyInteger` before. Then, when we initializing `TyInteger`, `U8` is not
    // initialized and `U8.INSTANCE` is null. So if `VALUES` is a field of
    // `TyInteger` class, it will be filled with null value instead of `U8`
    // We fixing it by moving fields from `companion object` an independent object
    private object TyIntegerValuesHolder {
        val DEFAULT = TyInteger.I32
        val VALUES = listOf(U8, U16, U32, U64, U128, USize, I8, I16, I32, I64, I128, ISize)
        val NAMES = VALUES.map { it.name }
    }

    companion object {
        val DEFAULT: TyInteger get() = TyIntegerValuesHolder.DEFAULT
        val VALUES: List<TyInteger> get() = TyIntegerValuesHolder.VALUES
        val NAMES: List<String> get() = TyIntegerValuesHolder.NAMES

        fun fromName(name: String): TyInteger? {
            return VALUES.find { it.name == name }
        }

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

sealed class TyFloat(override val name: String, val ordinal: Int) : TyNumeric() {

    // See TyIntegerValuesHolder
    private object TyFloatValuesHolder {
        val DEFAULT = TyFloat.F64
        val VALUES = listOf(F32, F64)
        val NAMES = VALUES.map { it.name }
    }

    companion object {
        val DEFAULT: TyFloat get() = TyFloatValuesHolder.DEFAULT
        val VALUES: List<TyFloat> get() = TyFloatValuesHolder.VALUES
        val NAMES: List<String> get() = TyFloatValuesHolder.NAMES

        fun fromName(name: String): TyFloat? {
            return VALUES.find { it.name == name }
        }

        fun fromSuffixedLiteral(literal: PsiElement): TyFloat? {
            val text = literal.text
            return VALUES.find { text.endsWith(it.name) }
        }
    }

    object F32: TyFloat("f32", 0)
    object F64: TyFloat("f64", 1)
}
