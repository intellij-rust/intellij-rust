package org.rust.lang.core.lexer

import com.intellij.lexer.DelegateLexer
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.psi.tree.IElementType

/**
 * Like [com.intellij.lexer.MergingLexerAdapterBase] but allows
 * acquiring more info about first token going to be merged.
 */
abstract class RustMergingLexerAdapterBase(original: Lexer) : DelegateLexer(original) {
    private var mergedTokenType: IElementType? = null
    private var mergedState: Int = 0
    private var mergedTokenStart: Int = 0

    /**
     * Advance original lexer ([getDelegate]) performing merging operation.
     * @return token type of merged token
     */
    protected abstract fun mergingAdvance(type: IElementType): IElementType

    override fun getState(): Int {
        ensureTokenLocated()
        return mergedState
    }

    override fun getTokenType(): IElementType? {
        ensureTokenLocated()
        return mergedTokenType
    }

    override fun getTokenStart(): Int {
        ensureTokenLocated()
        return mergedTokenStart
    }

    override fun getTokenEnd(): Int {
        ensureTokenLocated()
        return super.getTokenStart()
    }

    override fun advance() {
        mergedTokenType = null
    }

    private fun ensureTokenLocated() {
        if (mergedTokenType != null) return

        // Prepare first token to merge
        mergedTokenType = delegate.tokenType
        mergedTokenStart = delegate.tokenStart
        mergedState = delegate.state

        // Exit if we have reached eof
        if (mergedTokenType == null) return

        mergedTokenType = mergingAdvance(mergedTokenType as IElementType)
    }

    override fun getCurrentPosition(): LexerPosition =
        MyLexerPosition(mergedTokenStart, mergedTokenType, delegate.currentPosition, mergedState)

    override fun restore(position: LexerPosition) {
        if (position !is MyLexerPosition) {
            throw IllegalArgumentException("incompatible lexer position")
        } else {
            delegate.restore(position.originalPosition)
            mergedTokenType = position.tokenType
            mergedTokenStart = position.offset
            mergedState = position.oldState
        }
    }

    private data class MyLexerPosition(
        private val myOffset: Int,
        val tokenType: IElementType?,
        val originalPosition: LexerPosition,
        val oldState: Int
    ) : LexerPosition {
        override fun getOffset(): Int = myOffset
        override fun getState(): Int = originalPosition.state
    }
}
