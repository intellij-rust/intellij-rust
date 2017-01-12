package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsLiteralTokenType

class RsRawStringLiteralImpl(type: IElementType, text: CharSequence) : RsTextLiteralImplBase(type, text) {
    val hashes: Int
        get() = offsets.openDelim?.length?.let { it - 1 } ?: 0

    override val value: String?
        get() = valueString

    override fun toString(): String = "RustRawStringLiteralImpl($elementType)"

    override fun computeOffsets(): Offsets {
        val prefixEnd = locatePrefix()

        val hashes = countHashes(prefixEnd)

        val openDelimEnd = doLocate(prefixEnd) {
            assert(textLength - it >= 1 + hashes && text[it] == '#' || text[it] == '"') { "expected open delim" }
            it + 1 + hashes
        }

        val valueEnd = doLocate(openDelimEnd) { locateValue(it, hashes) }

        val closeDelimEnd = doLocate(valueEnd) {
            assert(textLength - it >= 1 + hashes && text[it] == '"') { "expected close delim" }
            it + 1 + hashes
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
        @JvmStatic fun createTokenType(debugName: String): RsLiteralTokenType =
            RsLiteralTokenType(debugName, ::RsRawStringLiteralImpl)
    }
}
