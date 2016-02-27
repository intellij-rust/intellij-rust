package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
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

    class Int(valueString: String) : RustNumber<Long>(valueString) {
        override fun doParse(vs: String): Long {
            val (start, radix) = when {
                vs.startsWith("0b") -> 2 to 2
                vs.startsWith("0o") -> 2 to 8
                vs.startsWith("0x") -> 2 to 16
                else                -> 0 to 10
            }
            return parseLong(vs.substring(start), radix)
        }
    }

    class Float(valueString: String) : RustNumber<Double>(valueString) {
        override fun doParse(vs: String): Double = vs.toDouble()
    }

    companion object {
        fun create(valueString: String, suffix: String?, tokenType: IElementType): RustNumber<*>? =
            fromSuffixString(valueString, suffix) ?: fromTokenType(valueString, tokenType)

        private fun fromSuffixString(valueString: String, suffix: String?): RustNumber<*>? = when (suffix?.get(0)) {
            'i', 'u' -> RustNumber.Int(valueString)
            'f'      -> RustNumber.Float(valueString)
            else     -> null
        }

        private fun fromTokenType(valueString: String, tokenType: IElementType): RustNumber<*>? = when (tokenType) {
            INTEGER_LITERAL -> RustNumber.Int(valueString)
            FLOAT_LITERAL   -> RustNumber.Float(valueString)
            else            -> null
        }
    }
}
