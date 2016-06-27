package org.rust.lang.core.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.lexer.MergeFunction
import com.intellij.lexer.MergingLexerAdapterBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.psi.RustTokenElementTypes.OUTER_EOL_DOC_COMMENT

class RustLexer : MergingLexerAdapterBase(FlexAdapter(_RustLexer())) {
    override fun getMergeFunction(): MergeFunction = MergeEolDocComments

    /**
     * Merge consecutive eol doc comments, including whitespace only *between* them.
     */
    private object MergeEolDocComments : MergeFunction {
        override fun merge(type: IElementType, lexer: Lexer): IElementType? {
            if (type == INNER_EOL_DOC_COMMENT || type == OUTER_EOL_DOC_COMMENT) {
                val prefix = if (type == INNER_EOL_DOC_COMMENT) "//!" else "///"
                while (isOnMergeableComment(lexer, type, prefix) || isOnWSBetweenComments(lexer, type, prefix)) {
                    lexer.advance()
                }
            }

            return type
        }

        private fun isOnMergeableComment(lexer: Lexer, type: IElementType, prefix: CharSequence) =
            lexer.tokenType == type && lexer.tokenSequence.startsWith(prefix)

        private fun isOnWSBetweenComments(lexer: Lexer, type: IElementType, prefix: CharSequence) =
            lexer.tokenType == WHITE_SPACE
                && StringUtil.getLineBreakCount(lexer.tokenSequence) == 1
                && lexer.peekingFrame { advance(); isOnMergeableComment(this, type, prefix) }
    }
}
