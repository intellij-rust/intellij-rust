package org.rust.lang.core.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.FLOAT_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.INTEGER_LITERAL

private val VALID_INTEGER_SUFFIXES = listOf("u8", "i8", "u16", "i16", "u32", "i32", "u64", "i64", "isize", "usize")
private val VALID_FLOAT_SUFFIXES = listOf("f32", "f64")
const val DEC_DIGIT = "0123456789"
const val BIN_DIGIT = "01"
const val OCT_DIGIT = "01234567"
const val HEX_DIGIT = "0123456789abcdefABCDEF"
const val NUM_OTHER_CHARS = "+-_."
const val EXP_CHARS = "eE"

class RustNumericLiteralImpl(type: IElementType, text: CharSequence) : RustLiteral.Number(type, text) {
    override val value: Any?
        get() = TODO() // TODO Implement this.

    override val possibleSuffixes: Collection<String>
        get() = when (tokenType) {
            INTEGER_LITERAL -> VALID_INTEGER_SUFFIXES
            FLOAT_LITERAL   -> VALID_FLOAT_SUFFIXES
            else            -> error("unreachable")
        }

    override fun toString(): String = "RustNumericLiteralImpl($tokenType)"

    override fun computeMetadata(): Metadata {
        val (start, digits) = when {
            text.startsWith("0b") -> 2 to BIN_DIGIT
            text.startsWith("0o") -> 2 to OCT_DIGIT
            text.startsWith("0x") -> 2 to HEX_DIGIT
            else                  -> 0 to DEC_DIGIT
        }

        var hasExponent = false
        text.substring(start).forEachIndexed { i, ch ->
            if (!hasExponent && EXP_CHARS.contains(ch)) {
                hasExponent = true
            } else if (!digits.contains(ch) && !NUM_OTHER_CHARS.contains(ch)) {
                return Metadata(
                    value = TextRange.create(0, i + start),
                    suffix = TextRange(i + start, textLength))
            }
        }

        return Metadata(value = TextRange.allOf(text))
    }

    companion object {
        @JvmStatic fun createTokenType(debugName: String): RustLiteralTokenType =
            RustLiteralTokenType(debugName, ::RustNumericLiteralImpl)
    }
}
