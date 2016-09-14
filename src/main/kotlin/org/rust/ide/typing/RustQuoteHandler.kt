package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.impl.RustRawStringLiteralImpl

// Remember not to auto-pair `'` in char literals because of lifetimes, which use a single `'`: `'a`
class RustQuoteHandler : SimpleTokenSetQuoteHandler(
    STRING_LITERAL,
    BYTE_STRING_LITERAL,
    RAW_STRING_LITERAL,
    RAW_BYTE_STRING_LITERAL
), MultiCharQuoteHandler {
    override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        val elementType = iterator.tokenType
        val start = iterator.start
        // FIXME: Hashes?
        return when (elementType) {
            RAW_BYTE_STRING_LITERAL ->
                offset - start <= 2
            BYTE_STRING_LITERAL, RAW_STRING_LITERAL ->
                offset - start <= 1
            else -> super.isOpeningQuote(iterator, offset)
        }
    }

    override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        return super.isClosingQuote(iterator, offset)
    }

    override fun isInsideLiteral(iterator: HighlighterIterator): Boolean =
        if (iterator.tokenType in StringEscapesTokenTypes.STRING_LITERAL_ESCAPES)
            true
        else
            super.isInsideLiteral(iterator)

    fun isDeepInsideLiteral(iterator: HighlighterIterator, offset: Int): Boolean {
        if (!isInsideLiteral(iterator)) {
            return false
        }

        val literal = getLiteralDumb(iterator) ?: return true // it's safer to fallback to true
        val offsets = literal.offsets ?: return true
        val relativeOffset = offset - iterator.start
        return offsets.value?.containsOffset(relativeOffset) ?: true
    }

    override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence? {
        val literal = getLiteralDumb(iterator) ?: return null

        if (literal is RustRawStringLiteralImpl) {
            return '"' + "#".repeat(literal.hashes)
        }

        return null
    }

    /**
     * Creates virtual [RustLiteral] PSI element assuming that it is represented as
     * single, contiguous token in highlighter, in other words - it doesn't contain
     * any escape sequences etc. (hence 'dumb').
     */
    private fun getLiteralDumb(iterator: HighlighterIterator): RustLiteral? {
        val start = iterator.start
        val end = iterator.end

        val document = iterator.document
        val text = document.charsSequence
        val literalText = CharSequenceSubSequence(text, start, end)

        val elementType = iterator.tokenType as? RustLiteralTokenType ?: return null
        return elementType.createLeafNode(literalText) as RustLiteral
    }
}
