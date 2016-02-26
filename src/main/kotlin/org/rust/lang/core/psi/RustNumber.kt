package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustNumber.Float.F32
import org.rust.lang.core.psi.RustNumber.Float.F64
import org.rust.lang.core.psi.RustNumber.Int.*
import org.rust.lang.core.psi.RustTokenElementTypes.FLOAT_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.INTEGER_LITERAL
import java.lang.Long.parseLong

sealed class RustNumber<out T>(val valueString: String) {
    val javaValue: T? by lazy {
        try {
            doParse(valueString.filter { it != '_' })
        } catch(e: NumberFormatException) {
            null
        }
    }

    protected abstract fun doParse(vs: String): T

    sealed class Int(valueString: String) : RustNumber<Long>(valueString) {
        override fun doParse(vs: String): Long {
            val (start, radix) = when {
                vs.startsWith("0b") -> 2 to 2
                vs.startsWith("0o") -> 2 to 8
                vs.startsWith("0x") -> 2 to 16
                else                -> 0 to 10
            }
            return parseLong(vs.substring(start), radix)
        }

        class I8(valueString: String) : Int(valueString)
        class I16(valueString: String) : Int(valueString)
        class I32(valueString: String) : Int(valueString)
        class I64(valueString: String) : Int(valueString)
        class ISize(valueString: String) : Int(valueString)

        class U8(valueString: String) : Int(valueString)
        class U16(valueString: String) : Int(valueString)
        class U32(valueString: String) : Int(valueString)
        class U64(valueString: String) : Int(valueString)
        class USize(valueString: String) : Int(valueString)
    }

    sealed class Float(valueString: String) : RustNumber<Double>(valueString) {
        override fun doParse(vs: String): Double = vs.toDouble()

        class F32(valueString: String) : Float(valueString)
        class F64(valueString: String) : Float(valueString)
    }

    companion object {
        fun create(valueString: String, suffix: String?, tokenType: IElementType): RustNumber<*>? =
            fromSuffixString(valueString, suffix) ?: fromTokenType(valueString, tokenType)

        private fun fromSuffixString(valueString: String, suffix: String?): RustNumber<*>? = when (suffix) {
            "i8"    -> I8(valueString)
            "i16"   -> I16(valueString)
            "i32"   -> I32(valueString)
            "i64"   -> I64(valueString)
            "isize" -> ISize(valueString)
            "u8"    -> U8(valueString)
            "u16"   -> U16(valueString)
            "u32"   -> U32(valueString)
            "u64"   -> U64(valueString)
            "usize" -> USize(valueString)
            "f32"   -> F32(valueString)
            "f64"   -> F64(valueString)
            else    -> null
        }

        private fun fromTokenType(valueString: String, tokenType: IElementType): RustNumber<*>? = when (tokenType) {
            INTEGER_LITERAL -> I32(valueString)
            FLOAT_LITERAL   -> F64(valueString)
            else            -> null
        }
    }
}
