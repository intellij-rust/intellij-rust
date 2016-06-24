package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes.INNER_DOC_COMMENT
import org.rust.lang.core.psi.RustTokenElementTypes.OUTER_DOC_COMMENT

/**
 * Merges consecutive eol doc comments with whitespace between.
 */
open class RustDocMergingLexerAdapter(original: Lexer) : RustMergingLexerAdapterBase(original) {
    override fun mergingAdvance(type: IElementType): IElementType {
        val prefix = delegate.tokenSequence.softSubSequence(0, 3)

        if ((type == INNER_DOC_COMMENT && prefix == "//!") || (type == OUTER_DOC_COMMENT && prefix == "///")) {
            loop@ do {
                // Do not consume trailing whitespace
                if (delegate.tokenType == WHITE_SPACE
                    && delegate.peekingFrame { delegate.advance(); !delegate.isOnMergeableComment(type, prefix) }) {
                    break@loop
                }

                delegate.advance()
            } while (delegate.isOnMergeableComment(type, prefix) || delegate.isOnWSBetweenComments())
        } else {
            delegate.advance()
        }

        return type
    }

    private fun Lexer.isOnMergeableComment(type: IElementType, prefix: CharSequence) =
        tokenType == type && tokenSequence.startsWith(prefix)

    private fun Lexer.isOnWSBetweenComments() = tokenType == WHITE_SPACE
        && tokenSequence.filter { it == '\r' || it == '\n' }.toString().isEOL()
}
