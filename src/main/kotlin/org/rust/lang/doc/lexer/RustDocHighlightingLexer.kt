package org.rust.lang.doc.lexer

import com.intellij.lexer.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.TokenSet
import com.intellij.util.text.CharSequenceSubSequence
import org.rust.lang.doc.psi.RustDocElementTypes.*
import org.rust.lang.doc.psi.RustDocKind


/**
 * Default lexer copies the whole `buf` in the [reset] method into an array.
 *
 * As we use this lexer as a layered lexer, [reset] is called a lot with `buf` containing the whole
 * file, and `start` and `end` pointing to the range of the documentation comment.
 *
 * This wrapper avoids coping the whole file.
 *
 * TODO: switch to the patched jFlex version from `intellij-community/tools/lexer`
 */
private class FlexWrapper(private val delegate: FlexLexer) : FlexLexer by delegate {
    private var myStart: Int = 0
    override fun getTokenStart(): Int = delegate.tokenStart + myStart
    override fun getTokenEnd(): Int = delegate.tokenEnd + myStart
    override fun reset(buf: CharSequence, start: Int, end: Int, initialState: Int) {
        myStart = start
        delegate.reset(CharSequenceSubSequence(buf, start, end), 0, end - start, initialState)
    }
}

class RustDocHighlightingLexer(kind: RustDocKind) :
    MergingLexerAdapter(
        FlexAdapter(FlexWrapper(_RustDocHighlightingLexer(null, kind.isBlock))),
        TOKENS_TO_MERGE
    ) {

    override fun getMergeFunction() = MergeFunction { type, lexer ->
        if (type == DOC_TEXT) {
            while (lexer.tokenType == type || isOnNonEolWS(lexer)) {
                lexer.advance()
            }

            type
        } else {
            super.getMergeFunction().merge(type, lexer)
        }
    }

    private fun isOnNonEolWS(lexer: Lexer) =
        lexer.tokenType == WHITE_SPACE && !StringUtil.containsLineBreak(lexer.tokenSequence)

    companion object {
        private val TOKENS_TO_MERGE = TokenSet.create(WHITE_SPACE, DOC_CODE_SPAN, DOC_CODE_FENCE)
    }
}
