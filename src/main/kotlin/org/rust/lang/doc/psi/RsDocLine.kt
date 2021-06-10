/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.util.text.CharArrayUtil
import com.intellij.util.text.CharSequenceSubSequence

data class RsDocLine(
    private val text: CharSequence,
    private val startOffset: Int,
    private val endOffset: Int,
    val contentStartOffset: Int = startOffset,
    private val contentEndOffset: Int = endOffset,
    val isLastLine: Boolean
) {
    init {
        require(contentEndOffset >= contentStartOffset) { "`$text`, $contentStartOffset, $contentEndOffset" }
    }

    val prefix: CharSequence get() = CharSequenceSubSequence(text, startOffset, contentStartOffset)
    val content: CharSequence get() = CharSequenceSubSequence(text, contentStartOffset, contentEndOffset)
    val suffix: CharSequence get() = CharSequenceSubSequence(text, contentEndOffset, endOffset)

    val hasPrefix: Boolean get() = startOffset != contentStartOffset
    val hasContent: Boolean get() = contentStartOffset != contentEndOffset
    val hasSuffix: Boolean get() = contentEndOffset != endOffset

    val contentLength: Int get() = contentEndOffset - contentStartOffset

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

    private fun shiftForwardWhitespace(): Int = CharArrayUtil.shiftForward(text, contentStartOffset, contentEndOffset, " \t")

    fun leadingWhitespace(): Int = shiftForwardWhitespace() - contentStartOffset

    fun startsWith(s: String): Boolean =
        CharArrayUtil.regionMatches(text, contentStartOffset, contentEndOffset, s)

    fun substring(startIndex: Int): RsDocLine {
        require(startIndex <= contentLength)
        return copy(contentStartOffset = contentStartOffset + startIndex)
    }

    fun indentBy(indent: Int): RsDocLine {
        return dropWhileAtMost(indent) { it == ' ' }
    }

    private inline fun dropWhileAtMost(n: Int, predicate: (Char) -> Boolean): RsDocLine {
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
            var prev = 0
            return generateSequence {
                if (prev == -1) return@generateSequence null
                val index = text.indexOf(char = '\n', startIndex = prev)
                if (index >= 0) {
                    val line = RsDocLine(text, startOffset = prev, endOffset = index, isLastLine = false)
                    prev = index + 1
                    line
                } else {
                    val line = RsDocLine(text, startOffset = prev, endOffset = text.length, isLastLine = true)
                    prev = -1
                    line
                }
            }
        }
    }
}
