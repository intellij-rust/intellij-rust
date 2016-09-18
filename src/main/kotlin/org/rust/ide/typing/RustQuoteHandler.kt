package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.StringEscapesTokenTypes.STRING_LITERAL_ESCAPES
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.impl.RustRawStringLiteralImpl

// Remember not to auto-pair `'` in char literals because of lifetimes, which use single `'`: `'a`
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
        // FIXME: Hashes?
        return super.isClosingQuote(iterator, offset)
    }

    override fun isInsideLiteral(iterator: HighlighterIterator): Boolean =
        if (iterator.tokenType in STRING_LITERAL_ESCAPES)
            true
        else
            super.isInsideLiteral(iterator)

    /**
     * Check whether caret is deep inside string literal,
     * i.e. it's inside contents itself, not decoration.
     */
    fun isDeepInsideLiteral(iterator: HighlighterIterator, offset: Int): Boolean {
        // First, filter out unwanted token types
        if (!isInsideLiteral(iterator)) return false

        val tt = iterator.tokenType
        val start = iterator.start

        // If we are inside raw literal then we don't have to deal with escapes
        if (tt == RAW_STRING_LITERAL || tt == RAW_BYTE_STRING_LITERAL) {
            return getLiteralDumb(iterator)?.offsets?.value?.containsOffset(offset - start) ?: false
        }

        // We have to deal with escapes here as we are inside (byte) string literal;
        // we could build huge virtual literal using something like [getLiteralDumb],
        // but that is expensive, especially for long strings with numerous escapes
        // while we wanna be fast & furious when user notices lags.

        // If we are inside escape then we must be deep inside literal
        if (tt in STRING_LITERAL_ESCAPES) return true

        // We can try to deduce our situation by just looking at neighbourhood...
        val (prev, next) = getSiblingTokens(iterator)

        // ... as we can be in the first token of the literal ...
        if (prev !in STRING_LITERAL_ESCAPES) return !isOpeningQuote(iterator, offset)
        // ... or the last one.
        if (next !in STRING_LITERAL_ESCAPES) return !isClosingQuote(iterator, offset - 1)

        // Otherwise we are inside
        return true
    }

    override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence? {
        val literal = getLiteralDumb(iterator) ?: return null

        if (literal is RustRawStringLiteralImpl) {
            return '"' + "#".repeat(literal.hashes)
        }

        return null
    }

    /**
     * Get previous and next token types relative to [iterator] position.
     */
    private fun getSiblingTokens(iterator: HighlighterIterator): Pair<IElementType?, IElementType?> {
        iterator.retreat()
        val prev = if (iterator.atEnd()) null else iterator.tokenType
        iterator.advance()

        iterator.advance()
        val next = if (iterator.atEnd()) null else iterator.tokenType
        iterator.retreat()

        return prev to next
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
