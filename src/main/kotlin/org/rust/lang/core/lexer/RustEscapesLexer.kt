package org.rust.lang.core.lexer

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.StringEscapesTokenTypes.*
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes.*

private const val BYTE_ESCAPE_LENGTH = "\\x00".length
private const val UNICODE_ESCAPE_MIN_LENGTH = "\\u{0}".length
private const val UNICODE_ESCAPE_MAX_LENGTH = "\\u{000000}".length

/**
 * Performs lexical analysis of Rust byte/char/string/byte string literals using Rust character escaping rules.
 */
class RustEscapesLexer constructor(val defaultToken: IElementType,
                                   val unicode: Boolean = false,
                                   val eol: Boolean = false) : LexerBaseEx() {
    override fun determineTokenType(): IElementType? {
        // We're at the end of the string token => finish lexing
        if (tokenStart >= tokenEnd) {
            return null
        }

        // We're not inside escape sequence
        if (bufferSequence[tokenStart] != '\\') {
            return defaultToken
        }

        // \ is at the end of the string token
        if (tokenStart + 1 >= tokenEnd) {
            return INVALID_CHARACTER_ESCAPE_TOKEN
        }

        return when (bufferSequence[tokenStart + 1]) {
            'u'                                 ->
                when {
                    !unicode                                   -> INVALID_CHARACTER_ESCAPE_TOKEN
                    isValidUnicodeEscape(tokenStart, tokenEnd) -> VALID_STRING_ESCAPE_TOKEN
                    else                                       -> INVALID_UNICODE_ESCAPE_TOKEN
                }
            'x'                                 -> esc(isValidByteEscape(tokenStart, tokenEnd))
            '\r', '\n'                          -> esc(eol)
            'n', 'r', 't', '0', '\\', '\'', '"' -> VALID_STRING_ESCAPE_TOKEN
            else                                -> INVALID_CHARACTER_ESCAPE_TOKEN
        }
    }

    override fun locateToken(start: Int): Int {
        if (start >= bufferEnd) {
            return start
        }

        if (bufferSequence[start] == '\\') {
            val i = start + 1

            if (i >= bufferEnd) {
                return bufferEnd
            }

            when (bufferSequence[i]) {
                'x'        ->
                    if (bufferEnd - (i + 1) >= 1 && StringUtil.isHexDigit(bufferSequence[i + 1])) {
                        if (bufferEnd - (i + 2) >= 1 && StringUtil.isHexDigit(bufferSequence[i + 2])) {
                            return i + 2 + 1
                        } else {
                            return i + 1 + 1
                        }
                    }
                'u'        ->
                    if (bufferEnd - (i + 1) >= 1 && bufferSequence[i + 1] == '{') {
                        val idx = bufferSequence.indexOf('}', i + 1)
                        return if (idx != -1) Math.min(idx + 1, bufferEnd) else bufferEnd
                    }
                '\r', '\n' -> {
                    var j = i
                    while (j < bufferEnd && bufferSequence[j].isRustWhitespaceChar()) {
                        j++
                    }
                    return j
                }
            }
            return i + 1
        } else {
            val idx = bufferSequence.indexOf('\\', start + 1)
            return if (idx != -1) Math.min(idx, bufferEnd) else bufferEnd
        }
    }

    private fun esc(test: Boolean): IElementType =
        if (test) VALID_STRING_ESCAPE_TOKEN else INVALID_CHARACTER_ESCAPE_TOKEN

    // https://doc.rust-lang.org/reference.html#byte-escapes
    // A byte escape escape starts with U+0078 (x) and is followed by
    // exactly two hex digits. It denotes the byte equal to the provided hex value.
    private fun isValidByteEscape(start: Int, end: Int): Boolean =
        end - start == BYTE_ESCAPE_LENGTH &&
            bufferSequence.startsWith("\\x", start) &&
            testCodepointRange(start + 2, end, 0xff)

    private fun isValidUnicodeEscape(start: Int, end: Int): Boolean =
        // FIXME: I'm not sure if this max codepoint is correct.
        // I've found it by playing with Rust Playground, so it matches rustc behaviour, but it has
        // nothing to do with the Rust Reference (I've expected 0x7fffff or something similar).
        end - start in UNICODE_ESCAPE_MIN_LENGTH..UNICODE_ESCAPE_MAX_LENGTH &&
            bufferSequence.startsWith("\\u{", start) && bufferSequence[end - 1] == '}' &&
            testCodepointRange(start + 3, end - 1, 0x10ffff)

    private fun testCodepointRange(start: Int, end: Int, max: Int): Boolean =
        try {
            Integer.parseInt(bufferSequence.substring(start, end), 16) <= max
        } catch (e: NumberFormatException) {
            false
        }

    companion object {
        /**
         * Create an instance of [RustEscapesLexer] suitable for given [IElementType].
         *
         * For the set of supported token types see [ESCAPABLE_LITERALS_TOKEN_SET].
         *
         * @throws IllegalArgumentException when given token type is unsupported
         */
        fun of(tokenType: IElementType): RustEscapesLexer = when (tokenType) {
            BYTE_LITERAL        -> RustEscapesLexer(BYTE_LITERAL)
            CHAR_LITERAL        -> RustEscapesLexer(CHAR_LITERAL, unicode = true)
            BYTE_STRING_LITERAL -> RustEscapesLexer(BYTE_STRING_LITERAL, eol = true)
            STRING_LITERAL      -> RustEscapesLexer(STRING_LITERAL, unicode = true, eol = true)
            else                -> throw IllegalArgumentException("unsupported literal type")
        }
    }
}

/**
 * Determines if the char is a Rust whitespace character.
 */
private fun Char.isRustWhitespaceChar(): Boolean = equals(' ') || equals('\r') || equals('\n') || equals('\t')
