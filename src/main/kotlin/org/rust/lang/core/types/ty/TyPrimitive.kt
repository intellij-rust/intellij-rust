/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPath

/**
 * These are "atomic" ty.
 *
 * Definition intentionally differs from the reference: we don't treat
 * tuples or arrays as primitive.
 */
abstract class TyPrimitive : Ty() {

    abstract val name: String

    companion object {
        fun fromPath(path: RsPath): TyPrimitive? {
            if (path.hasColonColon) return null
            val name = path.referenceName ?: return null

            TyInteger.fromName(name)?.let { return it }
            TyFloat.fromName(name)?.let { return it }

            return when (name) {
                "bool" -> TyBool.INSTANCE
                "char" -> TyChar.INSTANCE
                "str" -> TyStr.INSTANCE
                else -> null
            }
        }
    }
}

class TyBool : TyPrimitive() {
    override val name: String
        get() = "bool"

    companion object {
        val INSTANCE: TyBool = TyBool()
    }
}

class TyChar : TyPrimitive() {
    override val name: String
        get() = "char"

    companion object {
        val INSTANCE: TyChar = TyChar()
    }
}

class TyUnit : TyPrimitive() {
    override val name: String
        get() = "unit"

    companion object {
        val INSTANCE: TyUnit = TyUnit()
    }
}

/** The `!` type. E.g. `unimplemented!()` */
object TyNever : TyPrimitive() {
    override val name: String
        get() = "never"
}

class TyStr : TyPrimitive() {
    override val name: String
        get() = "str"

    companion object {
        val INSTANCE: TyStr = TyStr()
    }
}

abstract class TyNumeric : TyPrimitive()

sealed class TyInteger : TyNumeric() {
    abstract val ordinal: Int

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
        val DEFAULT = I32.INSTANCE
        val VALUES = listOf(U8.INSTANCE, U16.INSTANCE, U32.INSTANCE, U64.INSTANCE, U128.INSTANCE, USize.INSTANCE, I8.INSTANCE, I16.INSTANCE, I32.INSTANCE, I64.INSTANCE, I128.INSTANCE, ISize.INSTANCE)
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

    class U8: TyInteger() {
        override val name: String
            get() = "u8"
        override val ordinal: Int
            get() = 0

        companion object {
            val INSTANCE: U8 = U8()
        }
    }
    class U16: TyInteger() {
        override val name: String
            get() = "u16"
        override val ordinal: Int
            get() = 1

        companion object {
            val INSTANCE: U16 = U16()
        }
    }
    class U32: TyInteger() {
        override val name: String
            get() = "u32"
        override val ordinal: Int
            get() = 2

        companion object {
            val INSTANCE: U32 = U32()
        }
    }
    class U64: TyInteger() {
        override val name: String
            get() = "u64"
        override val ordinal: Int
            get() = 3

        companion object {
            val INSTANCE: U64 = U64()
        }
    }
    class U128: TyInteger() {
        override val name: String
            get() = "u128"
        override val ordinal: Int
            get() = 4

        companion object {
            val INSTANCE: U128 = U128()
        }
    }
    class USize : TyInteger() {
        override val name: String
            get() = "usize"
        override val ordinal: Int
            get() = 5

        companion object {
            val INSTANCE: USize = USize()
        }
    }

    class I8: TyInteger() {
        override val name: String
            get() = "i8"
        override val ordinal: Int
            get() = 6

        companion object {
            val INSTANCE: I8 = I8()
        }
    }
    class I16: TyInteger() {
        override val name: String
            get() = "i16"
        override val ordinal: Int
            get() = 7

        companion object {
            val INSTANCE: I16 = I16()
        }
    }
    class I32: TyInteger() {
        override val name: String
            get() = "i32"
        override val ordinal: Int
            get() = 8

        companion object {
            val INSTANCE: I32 = I32()
        }
    }
    class I64: TyInteger() {
        override val name: String
            get() = "i64"
        override val ordinal: Int
            get() = 9

        companion object {
            val INSTANCE: I64 = I64()
        }
    }
    class I128: TyInteger() {
        override val name: String
            get() = "i128"
        override val ordinal: Int
            get() = 10

        companion object {
            val INSTANCE: I128 = I128()
        }
    }
    class ISize: TyInteger() {
        override val name: String
            get() = "isize"
        override val ordinal: Int
            get() = 11

        companion object {
            val INSTANCE: ISize = ISize()
        }
    }
}

sealed class TyFloat : TyNumeric() {
    abstract val ordinal: Int

    // See TyIntegerValuesHolder
    private object TyFloatValuesHolder {
        val DEFAULT = F64.INSTANCE
        val VALUES = listOf(F32.INSTANCE, F64.INSTANCE)
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

    class F32: TyFloat() {
        override val name: String
            get() = "f32"
        override val ordinal: Int
            get() = 0

        companion object {
            val INSTANCE: F32 = F32()
        }
    }
    class F64: TyFloat() {
        override val name: String
            get() = "f64"
        override val ordinal: Int
            get() = 1

        companion object {
            val INSTANCE: F64 = F64()
        }
    }
}
