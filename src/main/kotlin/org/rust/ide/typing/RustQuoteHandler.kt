package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.impl.RustRawStringLiteralImpl

// Remember not to auto-pair `'` in char literals because of lifetimes, which use a single `'`: `'a`
class RustQuoteHandler : SimpleTokenSetQuoteHandler(STRING_LITERAL, BYTE_STRING_LITERAL,
    RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL), MultiCharQuoteHandler {
    override fun isOpeningQuote(iterator: HighlighterIterator?, offset: Int): Boolean {
        return super.isOpeningQuote(iterator, offset)
    }

    override fun isClosingQuote(iterator: HighlighterIterator?, offset: Int): Boolean {
        return super.isClosingQuote(iterator, offset)
    }

    fun isDeepInsideLiteral(iterator: HighlighterIterator, offset: Int): Boolean {
        if(!isInsideLiteral(iterator)) {
            return false
        }

        val literal = getLiteral(iterator) ?: return true // it's safer to fallback to true
        val offsets = literal.offsets ?: return true
        val relativeOffset = offset - iterator.start
        return offsets.value?.containsOffset(relativeOffset) ?: true
    }

    override fun getClosingQuote(iterator: HighlighterIterator, offset: Int): CharSequence? {
        val literal = getLiteral(iterator) ?: return null

        if (literal is RustRawStringLiteralImpl) {
            return '"' + StringUtil.repeatSymbol('#', literal.hashes)
        }

        return null
    }

    private fun getLiteral(iterator: HighlighterIterator): RustLiteral? {
        val start = iterator.start
        val end = iterator.end

        val document = iterator.document
        val text = document.charsSequence
        val literalText = CharSequenceSubSequence(text, start, end)

        val elementType = iterator.tokenType as? RustLiteralTokenType ?: return null
        return elementType.createLeafNode(literalText) as RustLiteral
    }

    private val IElementType.isRaw: Boolean
        get() = this == RAW_STRING_LITERAL || this == RAW_BYTE_STRING_LITERAL

    private val IElementType.isByte: Boolean
        get() = this == BYTE_STRING_LITERAL || this == RAW_BYTE_STRING_LITERAL
}
