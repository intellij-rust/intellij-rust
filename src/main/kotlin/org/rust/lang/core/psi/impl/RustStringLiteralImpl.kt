package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RustEscapesLexer
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.BYTE_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.CHAR_LITERAL
import org.rust.lang.utils.unescapeRust

class RustStringLiteralImpl(type: IElementType, text: CharSequence) : RustTextLiteralImplBase(type, text) {
    override val value: String?
        get() = valueString?.unescapeRust(RustEscapesLexer.of(elementType))

    override fun toString(): String = "RustStringLiteralImpl($elementType)"

    override fun computeOffsets(): Offsets {
        val quote = when (elementType) {
            BYTE_LITERAL, CHAR_LITERAL -> '\''
            else                       -> '"'
        }

        val prefixEnd = locatePrefix()

        val openDelimEnd = doLocate(prefixEnd) {
            assert(text[it] == quote) { "expected open delimiter `$quote` but found `${text[it]}`" }
            it + 1
        }

        val valueEnd = doLocate(openDelimEnd) { locateValue(it, quote) }

        val closeDelimEnd = doLocate(valueEnd) {
            assert(text[it] == quote) { "expected close delimiter `$quote` but found `${text[it]}`" }
            it + 1
        }

        return Offsets.fromEndOffsets(prefixEnd, openDelimEnd, valueEnd, closeDelimEnd, textLength)
    }

    private fun locateValue(start: Int, quote: Char): Int {
        var escape = false
        text.substring(start).forEachIndexed { i, ch ->
            if (escape) {
                escape = false
            } else when (ch) {
                '\\'  -> escape = true
                quote -> return i + start
            }
        }
        return textLength
    }

    companion object {
        @JvmStatic fun createTokenType(debugName: String): RustLiteralTokenType =
            RustLiteralTokenType(debugName, ::RustStringLiteralImpl)
    }
}
