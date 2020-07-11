/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.util.text.CharArrayUtil

data class RsDocLine(
    private val text: CharSequence,
    private val startOffset: Int,
    private val endOffset: Int,
    val contentStartOffset: Int = startOffset,
    private val contentEndOffset: Int = endOffset
) {
    init {
        check(contentEndOffset >= contentStartOffset) { "`$text`, $contentStartOffset, $contentEndOffset" }
    }

    val prefix: String get() = text.substring(startOffset, contentStartOffset)
    val content: String get() = text.substring(contentStartOffset, contentEndOffset)
    val suffix: String get() = text.substring(contentEndOffset, endOffset)

    fun removePrefix(delimiter: String): RsDocLine {
        return if (CharArrayUtil.regionMatches(text, contentStartOffset, contentEndOffset, delimiter)) {
            copy(contentStartOffset = contentStartOffset + delimiter.length, contentEndOffset = contentEndOffset)
        } else {
            this
        }
    }

    fun trimStart(): RsDocLine {
        val newOffset = shiftForwardWhitespace()
        return copy(contentStartOffset = newOffset, contentEndOffset = contentEndOffset)
    }

    private fun shiftForwardWhitespace() = CharArrayUtil.shiftForward(text, contentStartOffset, contentEndOffset, " \t")

    fun leadingWhitespace() = shiftForwardWhitespace() - contentStartOffset

    fun startsWith(s: String): Boolean =
        CharArrayUtil.regionMatches(text, contentStartOffset, contentEndOffset, s)

    fun indentBy(indent: Int): RsDocLine {
        return dropWhileAtMost(indent) { it == ' ' }
    }

    private fun dropWhileAtMost(n: Int, predicate: (Char) -> Boolean): RsDocLine {
        var i = n
        for (index in contentStartOffset until contentEndOffset) {
            if (i-- <= 0 || !predicate(text[index])) {
                return copy(contentStartOffset = index)
            }
        }
        return copy(contentStartOffset = contentEndOffset, contentEndOffset = contentEndOffset)
    }

    /**
     * Get rid of trailing (pseudo-regexp): `[ ]+ [*]* * /`
     */
    fun trimTrailingAsterisks(): RsDocLine {
        if (endOffset - startOffset < 2) return this

        var i = contentEndOffset - 1
        if (text.get(i - 1) == '*' && text.get(i) == '/') {
            i -= 2
            while (i >= contentStartOffset && text.get(i) == '*') i--
            while (i >= contentStartOffset && text.get(i) == ' ') i--

            return copy(contentStartOffset = contentStartOffset, contentEndOffset = i + 1)
        }

        return this
    }

    companion object {
        fun splitLines(text: CharSequence): Sequence<RsDocLine> {
            return sequence {
                var prev = 0
                while (true) {
                    val index = text.indexOf(char = '\n', startIndex = prev)
                    if (index > 0) {
                        yield(RsDocLine(text, prev, index))
                        prev = index + 1
                    } else {
                        yield(RsDocLine(text, prev, text.length))
                        break
                    }
                }
            }
        }
    }
}
