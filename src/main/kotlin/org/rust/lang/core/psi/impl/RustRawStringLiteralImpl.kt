package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteralTokenType

class RustRawStringLiteralImpl(type: IElementType, text: CharSequence) : RustTextLiteralImplBase(type, text) {
    private var _hashes: Int = 0

    val hashes: Int
        get() {
            @Suppress("UNUSED_VARIABLE")
            val o = offsets // ensure #computeOffsets() has been called
            return _hashes
        }

    override val value: String?
        get() = valueString

    override fun toString(): String = "RustRawStringLiteralImpl($elementType)"

    override fun computeOffsets(): Offsets {
        val prefixEnd = locatePrefix()

        _hashes = countHashes(prefixEnd)

        val openDelimEnd = doLocate(prefixEnd) {
            assert(textLength - it >= 1 + _hashes && text[it] == '#' || text[it] == '"') { "expected open delim" }
            it + 1 + _hashes
        }

        val valueEnd = doLocate(openDelimEnd) { locateValue(it, _hashes) }

        val closeDelimEnd = doLocate(valueEnd) {
            assert(textLength - it >= 1 + _hashes && text[it] == '"') { "expected close delim" }
            it + 1 + _hashes
        }

        return Offsets.fromEndOffsets(prefixEnd, openDelimEnd, valueEnd, closeDelimEnd, textLength)
    }

    private fun countHashes(start: Int): Int {
        var pos = start
        while (pos < textLength && text[pos] == '#') {
            pos++
        }
        return pos - start
    }

    private fun locateValue(start: Int, hashes: Int): Int {
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
