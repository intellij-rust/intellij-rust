package org.rust.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.LiteralTokenTypes
import org.rust.lang.core.psi.RustTokenElementTypes.*

/**
 * A family of lexers splitting Rust literals into distinguishable parts.
 *
 * All lexers assume, that their input is a valid Rust lexer (except, it may be incomplete). For example, following
 * inputs will do: `r####"moo"##abs`, `abc'abc'abc`, `b'a`, `''`, `r###`; but following ones will cause a crash:
 * `r$$$abc$$$`, `xd123`, `a.1`, `"abc"`+[forCharLiterals].
 */
abstract class RustLiteralLexer : LexerBaseEx() {
    companion object {
        /**
         * Create an instance of [RustLiteralLexer] able to handle [INTEGER_LITERAL] and [FLOAT_LITERAL].
         */
        fun forNumericLiterals(): RustLiteralLexer = RustNumericLiteralLexer()

        /**
         * Create an instance of [RustLiteralLexer] able to handle [CHAR_LITERAL], [BYTE_LITERAL].
         */
        fun forCharLiterals(): RustLiteralLexer = RustQuotedLiteralLexer(quote = '\'')

        /**
         * Create an instance of [RustLiteralLexer] able to handle [STRING_LITERAL], [BYTE_STRING_LITERAL].
         */
        fun forStringLiterals(): RustLiteralLexer = RustQuotedLiteralLexer(quote = '\"')

        /**
         * Create an instance of [RustLiteralLexer] able to handle [RAW_STRING_LITERAL] and [RAW_BYTE_STRING_LITERAL].
         */
        fun forRawStringLiterals(): RustLiteralLexer = RustRawStringLiteralLexer()

        /**
         * Create an instance of [RustLiteralLexer] suitable for given [IElementType].
         *
         * For the set of supported token types see [LITERALS_TOKEN_SET].
         *
         * @throws IllegalArgumentException when given token type is unsupported
         */
        fun of(tokenType: IElementType): RustLiteralLexer = when (tokenType) {
            INTEGER_LITERAL,
            FLOAT_LITERAL           -> forNumericLiterals()

            CHAR_LITERAL,
            BYTE_LITERAL            -> forCharLiterals()

            STRING_LITERAL,
            BYTE_STRING_LITERAL     -> forStringLiterals()

            RAW_STRING_LITERAL,
            RAW_BYTE_STRING_LITERAL -> forRawStringLiterals()

            else                    -> throw IllegalArgumentException("unsupported literal type")
        }
    }
}

private class RustNumericLiteralLexer : RustLiteralLexer() {
    override fun determineTokenType(): IElementType? =
        if (tokenStart >= tokenEnd) {
            null
        } else when (state) {
            IN_VALUE  -> LiteralTokenTypes.VALUE
            IN_SUFFIX -> LiteralTokenTypes.SUFFIX
            else      -> error("unreachable")
        }

    override fun locateToken(start: Int): Int =
        if (start >= bufferEnd) {
            start
        } else when (nextState) {
            IN_VALUE  -> locateValue(start)
            IN_SUFFIX -> Math.max(start, bufferEnd)
            else      -> error("unreachable")
        }

    private fun locateValue(start: Int): Int {
        var hasExponent = false
        val (vstart, digits) = when {
            bufferSequence.startsWith("0b", start) -> (start + 2) to BIN_DIGIT
            bufferSequence.startsWith("0o", start) -> (start + 2) to OCT_DIGIT
            bufferSequence.startsWith("0x", start) -> (start + 2) to HEX_DIGIT
            else                                   -> start to DEC_DIGIT
        }

        for (i in vstart..(bufferEnd - 1)) {
            val ch = bufferSequence[i]

            if (!hasExponent && EXP_CHARS.contains(ch)) {
                hasExponent = true
                continue
            }

            if (!digits.contains(ch) && !NUM_OTHER_CHARS.contains(ch)) {
                nextState = IN_SUFFIX
                return i
            }
        }

        return bufferEnd
    }

    companion object {
        // States
        const val IN_VALUE = 0
        const val IN_SUFFIX = 1

        // Utility charsets
        const val DEC_DIGIT = "0123456789"
        const val BIN_DIGIT = "01"
        const val OCT_DIGIT = "01234567"
        const val HEX_DIGIT = "0123456789abcdefABCDEF"
        const val NUM_OTHER_CHARS = "+-_."
        const val EXP_CHARS = "eE"
    }
}

private abstract class RustDelimitedLiteralLexerBase : RustLiteralLexer() {
    protected abstract fun locateOpenDelim(start: Int): Int
    protected abstract fun locateCloseDelim(start: Int): Int

    protected abstract fun locateValue(start: Int): Int

    override fun determineTokenType(): IElementType? =
        if (tokenStart >= tokenEnd) {
            null
        } else when (state) {
            IN_PREFIX      -> LiteralTokenTypes.PREFIX
            IN_OPEN_DELIM,
            IN_CLOSE_DELIM -> LiteralTokenTypes.DELIMITER
            IN_VALUE       -> LiteralTokenTypes.VALUE
            IN_SUFFIX      -> LiteralTokenTypes.SUFFIX
            else           -> error("unreachable")
        }

    override fun locateToken(start: Int): Int =
        if (start >= bufferEnd) {
            start
        } else when (state) {
            IN_PREFIX      -> {
                val pos = locatePrefix(start)
                nextState = IN_OPEN_DELIM
                if (pos == start) {
                    // If there is no prefix, scan for open delimiter
                    state = nextState
                    locateToken(start)
                } else {
                    pos
                }
            }

            IN_OPEN_DELIM  -> {
                val pos = locateOpenDelim(start)
                nextState = IN_VALUE
                pos
            }

            IN_VALUE       -> {
                val pos = locateValue(start)
                nextState = IN_CLOSE_DELIM
                if (pos == start) {
                    // If there is no value, scan for close delimiter
                    state = nextState
                    locateToken(start)
                } else {
                    pos
                }
            }

            IN_CLOSE_DELIM -> {
                val pos = locateCloseDelim(start)
                nextState = IN_SUFFIX
                pos
            }

            IN_SUFFIX      -> bufferEnd

            else           -> error("unreachable")
        }

    protected fun locatePrefix(start: Int): Int {
        for (i in start..(bufferEnd - 1)) {
            if (!bufferSequence[i].isLetter()) {
                return i
            }
        }
        return bufferEnd
    }

    companion object {
        // States
        const val IN_PREFIX = 0
        const val IN_OPEN_DELIM = 1
        const val IN_CLOSE_DELIM = 2
        const val IN_VALUE = 3
        const val IN_SUFFIX = 4
    }
}

private class RustQuotedLiteralLexer(private val quote: Char) : RustDelimitedLiteralLexerBase() {
    override fun locateOpenDelim(start: Int): Int = locateDelim(start)
    override fun locateCloseDelim(start: Int): Int = locateDelim(start)

    fun locateDelim(start: Int): Int {
        assert(bufferSequence[start] == quote)
        return start + 1
    }

    override fun locateValue(start: Int): Int {
        var escape = false
        for (i in start..(bufferEnd - 1)) {
            if (escape) {
                escape = false
            } else when (bufferSequence[i]) {
                '\\'  -> escape = true
                quote -> return i
            }
        }
        return bufferEnd
    }
}

private class RustRawStringLiteralLexer() : RustDelimitedLiteralLexerBase() {
    private var hashes = 0

    override fun locateOpenDelim(start: Int): Int {
        assert(bufferSequence[start] == '#' || bufferSequence[start] == '"')
        var pos = start
        while (pos < bufferEnd && bufferSequence[pos] == '#') {
            hashes++
            pos++
        }
        if (pos < bufferEnd) {
            assert(bufferSequence[pos] == '"')
            return pos + 1
        } else {
            return bufferEnd
        }
    }

    override fun locateCloseDelim(start: Int): Int {
        assert(bufferSequence[start] == '"')
        return start + 1 + hashes
    }

    override fun locateValue(start: Int): Int {
        for (i in start..(bufferEnd - 1)) {
            if (i + hashes < bufferEnd && bufferSequence[i] == '"' &&
                bufferSequence.subSequence(i + 1, i + 1 + hashes).all { it == '#' }) {
                return i
            }
        }
        return bufferEnd
    }
}
