package org.rust.lang.core.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustNumber
import org.rust.lang.core.psi.RustTokenElementTypes.FLOAT_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.INTEGER_LITERAL
import org.rust.lang.core.psi.visitors.RustVisitorEx

class RustNumericLiteralImpl(type: IElementType, text: CharSequence) : RustLiteral.Number(type, text) {
    override val value: RustNumber<*>?
        get() = valueString?.let { RustNumber.create(it, suffix, tokenType) }

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
