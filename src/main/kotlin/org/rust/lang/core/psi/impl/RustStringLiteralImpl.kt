package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RustEscapesLexer
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.BYTE_LITERAL
import org.rust.lang.core.psi.RustTokenElementTypes.CHAR_LITERAL
import org.rust.lang.utils.unescapeRust

class RustStringLiteralImpl(type: IElementType, text: CharSequence) : RustTextLiteralImplBase(type, text) {
    override val value: String?
        get() = valueString?.unescapeRust(RustEscapesLexer.of(tokenType))

    override fun toString(): String = "RustStringLiteralImpl($tokenType)"

    private val quote: Char
        get() = when (tokenType) {
            BYTE_LITERAL,
            CHAR_LITERAL -> '\''
            else         -> '"'
        }

    override fun locateOpenDelim(start: Int): Int = locateDelim(start)
    override fun locateCloseDelim(start: Int): Int = locateDelim(start)

    private fun locateDelim(start: Int): Int {
        assert(start < textLength) { "start is after textLength" }
        assert(text[start] == quote) { "expected delimiter `$quote` but found `${text[start]}`" }
        return start + 1
    }

    override fun locateValue(start: Int): Int {
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
