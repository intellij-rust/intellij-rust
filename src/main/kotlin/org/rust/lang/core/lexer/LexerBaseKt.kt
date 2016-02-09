package org.rust.lang.core.lexer

import com.intellij.lexer.LexerBase

/**
 * Small utility class to ease implementing [LexerBase] in Kotlin.
 */
abstract class LexerBaseKt : LexerBase() {
    private var state: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private lateinit var bufferSequence: CharSequence
    private var bufferEnd: Int = 0

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
