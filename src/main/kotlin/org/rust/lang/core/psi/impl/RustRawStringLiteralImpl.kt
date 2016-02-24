package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType

class RustRawStringLiteralImpl(type: IElementType, text: CharSequence) : RustLiteral.Text(type, text) {
    override val value: String?
        get() = valueString

    override fun toString(): String = "RustRawStringLiteralImpl($tokenType)"

    private var hashes = 0

    override fun locateOpenDelim(start: Int): Int {
        assert(text[start] == '#' || text[start] == '"') { "expected open delim" }
        var pos = start
        while (pos < textLength && text[pos] == '#') {
            hashes++
            pos++
        }
        if (pos < textLength) {
            assert(text[pos] == '"') { "expected \" but found ${text[pos]}" }
            return pos + 1
        } else {
            return textLength
        }
    }

    override fun locateCloseDelim(start: Int): Int {
        assert(textLength - start >= 1 + hashes && text[start] == '"') { "expected close delim" }
        return start + 1 + hashes
    }

    override fun locateValue(start: Int): Int {
        text.substring(start).forEachIndexed { i, ch ->
            if (start + i + hashes < textLength &&
                ch == '"' &&
                text.subSequence(start + i + 1, start + i + 1 + hashes).all { it == '#' }) {
                return i + start
            }
        }
        return textLength
    }

    companion object {
        @JvmStatic fun createTokenType(debugName: String): RustLiteralTokenType =
            RustLiteralTokenType(debugName, ::RustRawStringLiteralImpl)
    }
}
