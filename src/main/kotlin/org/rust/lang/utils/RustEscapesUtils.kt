package org.rust.lang.utils

import com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
import org.rust.lang.core.lexer.RustEscapesLexer
import org.rust.lang.core.psi.RustTokenElementTypes.STRING_LITERAL

/**
 * Unescape string escaped using Rust escaping rules.
 */
fun String.unescapeRust(unicode: Boolean = true, eol: Boolean = true): String {
    val sb = StringBuilder(length)
    val lexer = RustEscapesLexer(STRING_LITERAL, unicode, eol)
    lexer.start(this)
    while (lexer.tokenType != null) {
        sb.append(when (lexer.tokenType) {
            VALID_STRING_ESCAPE_TOKEN -> decodeEscape(lexer.tokenText)
            else                      -> lexer.tokenText
        })
        lexer.advance()
    }
    return sb.toString()
}

private fun decodeEscape(esc: String): String = when (esc) {
    "\\n"  -> "\n"
    "\\r"  -> "\r"
    "\\t"  -> "\t"
    "\\0"  -> "\u0000"
    "\\\\" -> "\\"
    "\\'"  -> "\'"
    "\\\"" -> "\""

    else   -> {
        assert(esc.length >= 2)
        assert(esc[0] == '\\')
        when (esc[1]) {
            'x'        -> Integer.parseUnsignedInt(esc.substring(2), 16).toChar().toString()
            'u'        -> Integer.parseUnsignedInt(esc.substring(3, esc.length - 1), 16).toChar().toString()
            '\r', '\n' -> ""
            else       -> error("unreachable")
        }
    }
}
