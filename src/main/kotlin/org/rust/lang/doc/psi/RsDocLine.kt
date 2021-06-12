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
    val isLastLine: Boolean,
    val isRemoved: Boolean = false,
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

    fun removePrefix(prefix: String): RsDocLine {
        return if (CharArrayUtil.regionMatches(text, contentStartOffset, contentEndOffset, prefix)) {
            copy(contentStartOffset = contentStartOffset + prefix.length, contentEndOffset = contentEndOffset)
        } else {
            this
        }
    }

    fun removeSuffix(suffix: String): RsDocLine {
        if (contentLength < suffix.length) return this
        return if (CharArrayUtil.regionMatches(text, contentEndOffset - suffix.length, contentEndOffset, suffix)) {
            copy(contentStartOffset = contentStartOffset, contentEndOffset = contentEndOffset - suffix.length)
        } else {
            this
        }
    }

    fun markRemoved(): RsDocLine {
        return copy(contentEndOffset = contentStartOffset, isRemoved = true)
    }

    fun trimStart(): RsDocLine {
        val newOffset = shiftForwardWhitespace()
        return copy(contentStartOffset = newOffset, contentEndOffset = contentEndOffset)
    }

    fun countStartWhitespace(): Int {
        return shiftForwardWhitespace() - contentStartOffset
    }

    private fun shiftForwardWhitespace(): Int = CharArrayUtil.shiftForward(text, contentStartOffset, contentEndOffset, " \t")

    fun substring(startIndex: Int): RsDocLine {
        require(startIndex <= contentLength)
        return copy(contentStartOffset = contentStartOffset + startIndex)
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
