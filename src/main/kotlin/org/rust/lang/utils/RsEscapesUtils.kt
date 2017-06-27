/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.psi.StringEscapesTokenTypes.*
import org.rust.lang.core.lexer.RustEscapesLexer
import org.rust.lang.core.lexer.tokenize

/**
 * Unescape string escaped using Rust escaping rules.
 */
fun String.unescapeRust(unicode: Boolean = true, eol: Boolean = true, extendedByte: Boolean = true): String =
    this.unescapeRust(RustEscapesLexer.dummy(unicode, eol, extendedByte))

/**
 * Unescape string escaped using Rust escaping rules.
 */
fun String.unescapeRust(escapesLexer: RustEscapesLexer): String =
    this.tokenize(escapesLexer)
        .joinToString(separator = "") {
            val (type, text) = it
            when (type) {
                VALID_STRING_ESCAPE_TOKEN -> decodeEscape(text)
                else -> text
            }
        }

/**
 * Mimics [com.intellij.codeInsight.CodeInsightUtilCore.parseStringCharacters], but obeys Rust escaping rules.
 */
fun parseRustStringCharacters(chars: String, outChars: StringBuilder): IntArray? {
    val sourceOffsets = IntArray(chars.length + 1)
    val outOffset = outChars.length
    var index = 0
    for ((type, text) in chars.tokenize(RustEscapesLexer.dummy())) {
        when (type) {
            VALID_STRING_ESCAPE_TOKEN -> {
                outChars.append(decodeEscape(text))
                // Set offset for the decoded character to the beginning of the escape sequence.
                sourceOffsets[outChars.length - outOffset - 1] = index
                // And perform a "jump"
                index += text.length
            }

            INVALID_CHARACTER_ESCAPE_TOKEN,
            INVALID_UNICODE_ESCAPE_TOKEN ->
                return null

            else -> {
                val first = outChars.length - outOffset
                outChars.append(text)
                val last = outChars.length - outOffset - 1
                // Set offsets for each character of given chunk
                for (i in first..last) {
                    sourceOffsets[i] = index
                    index++
                }
            }
        }
    }

    return sourceOffsets
}

private fun decodeEscape(esc: String): String = when (esc) {
    "\\n" -> "\n"
    "\\r" -> "\r"
    "\\t" -> "\t"
    "\\0" -> "\u0000"
    "\\\\" -> "\\"
    "\\'" -> "\'"
    "\\\"" -> "\""

    else -> {
        assert(esc.length >= 2)
        assert(esc[0] == '\\')
        when (esc[1]) {
            'x' -> Integer.parseInt(esc.substring(2), 16).toChar().toString()
            'u' -> Integer.parseInt(esc.substring(3, esc.length - 1), 16).toChar().toString()
            '\r', '\n' -> ""
            else -> error("unreachable")
        }
    }
}
