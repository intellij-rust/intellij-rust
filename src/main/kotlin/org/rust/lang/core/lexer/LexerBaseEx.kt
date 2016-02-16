package org.rust.lang.core.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Small utility class to ease implementing [LexerBase].
 */
abstract class LexerBaseEx : LexerBase() {
    private var state: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private lateinit var bufferSequence: CharSequence
    private var bufferEnd: Int = 0
    private var myTokenType: Lazy<IElementType?> = lazyOf(null)

    /**
     * Determine type of the current token (the one delimited by [tokenStart] and [tokenEnd]).
     */
    protected abstract fun determineTokenType(): IElementType?

    /**
     * Find next token location (the one starting with [tokenEnd] and ending somewhere).
     * @return end offset of the next token
     */
    protected abstract fun locateToken(start: Int): Int

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        bufferSequence = buffer
        bufferEnd = endOffset
        state = initialState

        tokenStart = startOffset
        tokenEnd = locateToken(tokenStart)
        myTokenType = lazy { determineTokenType() }
    }

    override fun advance() {
        tokenStart = tokenEnd
        tokenEnd = locateToken(tokenStart)
        myTokenType = lazy { determineTokenType() }
    }

    override fun getTokenType(): IElementType? = myTokenType.value

    override fun getState(): Int = state

    protected fun setState(state: Int) {
        this.state = state
    }

    override fun getTokenStart(): Int = tokenStart

    protected fun setTokenStart(tokenStart: Int) {
        this.tokenStart = tokenStart
    }

    override fun getTokenEnd(): Int = tokenEnd

    protected fun setTokenEnd(tokenEnd: Int) {
        this.tokenEnd = tokenEnd
    }

    override fun getBufferSequence(): CharSequence = bufferSequence

    protected fun setBufferSequence(bufferSequence: CharSequence) {
        this.bufferSequence = bufferSequence
    }

    override fun getBufferEnd(): Int = bufferEnd

    protected fun setBufferEnd(bufferEnd: Int) {
        this.bufferEnd = bufferEnd
    }
}
