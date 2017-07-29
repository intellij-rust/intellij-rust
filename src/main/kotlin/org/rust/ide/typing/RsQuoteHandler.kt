/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.StringEscapesTokenTypes.STRING_LITERAL_ESCAPES
import com.intellij.psi.TokenType.WHITE_SPACE
import org.rust.lang.core.psi.RS_RAW_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*

// Remember not to auto-pair `'` in char literals because of lifetimes, which use single `'`: `'a`
class RsQuoteHandler : SimpleTokenSetQuoteHandler(
    BYTE_LITERAL,
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
            BYTE_LITERAL -> offset == start + 1
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

    override fun isNonClosedLiteral(iterator: HighlighterIterator, chars: CharSequence): Boolean {
        if (iterator.tokenType == BYTE_LITERAL) {
            return iterator.end - iterator.start == 2
        }
        if (iterator.tokenType in RS_RAW_LITERALS) {
            val lastChar = chars[iterator.end - 1]
            return lastChar != '#' && lastChar != '"'
        }
        if (super.isNonClosedLiteral(iterator, chars)) return true
        // Rust allows multiline literals, so an unclosed quote will
        // match with an opening quote of the next literal.
        // Let's employ heuristics to find out if it is the case!
        // Specifically, check if the next token can't appear
        // in valid Rust code.
        val nextChar = chars.getOrElse(iterator.end, { '"' })
        if (nextChar == '"') return true

        iterator.advance()
        if (iterator.tokenType == WHITE_SPACE) {
            iterator.advance()
        }
        when (iterator.tokenType) {
            IDENTIFIER, INTEGER_LITERAL, FLOAT_LITERAL -> return true
        }
        return false
    }

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
        if (literal.node.elementType !in RS_RAW_LITERALS) return null

        val hashes = literal.offsets.openDelim?.length?.let { it - 1 } ?: 0
        return '"' + "#".repeat(hashes)
    }
}
