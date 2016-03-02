package org.rust.lang.core.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.FLOAT_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.INTEGER_LITERAL
import org.rust.lang.core.psi.visitors.RustVisitorEx

class RustNumericLiteralImpl(type: IElementType, text: CharSequence) : RustLiteral.Number(type, text) {
    override val valueAsLong: Long?
        get() = this.valueString
            ?.filter { it != '_' }
            ?.let {
                // We do not expect negative values, because they are treated as
                // unary expressions, not literals, in our lexing/parsing code.
                val (start, radix) = when (it.take(2)) {
                    "0b" -> 2 to 2
                    "0o" -> 2 to 8
                    "0x" -> 2 to 16
                    else -> 0 to 10
                }
                try {
                    java.lang.Long.parseLong(it.substring(start), radix)
                    // TODO: Replace this with: (when we migrate to Java 8)
                    // java.lang.Long.parseUnsignedLong(it.substring(start), radix)
                } catch(e: NumberFormatException) {
                    null
                }
            }

    override val valueAsDouble: Double?
        get() = this.valueString
            ?.filter { it != '_' }
            ?.let {
                try {
                    it.toDouble()
                } catch(e: NumberFormatException) {
                    null
                }
            }

    override val possibleSuffixes: Collection<String>
        get() = when (tokenType) {
            INTEGER_LITERAL -> VALID_INTEGER_SUFFIXES
            FLOAT_LITERAL   -> VALID_FLOAT_SUFFIXES
            else            -> error("unreachable")
        }

    override val isInt: Boolean
        get() = tokenType == INTEGER_LITERAL

    override val isFloat: Boolean
        get() = tokenType == FLOAT_LITERAL

    override fun toString(): String = "RustNumericLiteralImpl($tokenType)"

    override fun computeOffsets(): Offsets {
        val (start, digits) = when (text.take(2)) {
            "0b" -> 2 to BIN_DIGIT
            "0o" -> 2 to OCT_DIGIT
            "0x" -> 2 to HEX_DIGIT
            else -> 0 to DEC_DIGIT
        }

        var hasExponent = false
        text.substring(start).forEachIndexed { i, ch ->
            if (!hasExponent && ch in EXP_CHARS) {
                hasExponent = true
            } else if (ch !in digits && ch !in NUM_OTHER_CHARS) {
                return Offsets(
                    value = TextRange.create(0, i + start),
                    suffix = TextRange(i + start, textLength))
            }
        }

        return Offsets(value = TextRange.allOf(text))
    }

    override fun accept(visitor: PsiElementVisitor) = when (visitor) {
        is RustVisitorEx -> visitor.visitNumericLiteral(this)
        else             -> super.accept(visitor)
    }

    companion object {
        private val VALID_INTEGER_SUFFIXES = listOf("u8", "i8", "u16", "i16", "u32", "i32", "u64", "i64", "isize", "usize")
        private val VALID_FLOAT_SUFFIXES = listOf("f32", "f64")
        private const val DEC_DIGIT = "0123456789"
        private const val BIN_DIGIT = "01"
        private const val OCT_DIGIT = "01234567"
        private const val HEX_DIGIT = "0123456789abcdefABCDEF"
        private const val NUM_OTHER_CHARS = "+-_."
        private const val EXP_CHARS = "eE"

        @JvmStatic fun createTokenType(debugName: String): RustLiteralTokenType =
            RustLiteralTokenType(debugName, ::RustNumericLiteralImpl)
    }
}
