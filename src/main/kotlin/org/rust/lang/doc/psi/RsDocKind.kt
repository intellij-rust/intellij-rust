/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.INNER_EOL_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_BLOCK_DOC_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.OUTER_EOL_DOC_COMMENT
import org.rust.stdext.nextOrNull

enum class RsDocKind {
    Attr {
        override val prefix: String = ""
        override val infix: String = ""

        override fun removeDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> =
            lines // nothing to do here
    },

    InnerBlock {
        override val prefix: String = "/*!"
        override val infix: String = "*"

        override fun removeDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> =
            removeBlockDecoration(lines)
    },

    OuterBlock {
        override val prefix: String = "/**"
        override val infix: String = "*"

        override fun removeDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> =
            removeBlockDecoration(lines)
    },

    InnerEol {
        override val infix: String = "//!"
        override val prefix: String = infix

        override fun removeDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> =
            removeEolDecoration(lines)
    },

    OuterEol {
        override val infix: String = "///"
        override val prefix: String = infix

        override fun removeDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> =
            removeEolDecoration(lines)
    };

    abstract val prefix: String
    abstract val infix: String

    val isBlock: Boolean
        get() = this == InnerBlock || this == OuterBlock

    /**
     * Removes doc comment decoration from a sequence of token's lines.
     *
     * This method expects non-empty line sequences of valid doc comment tokens.
     * It does **not** perform any validation!
     */
    protected abstract fun removeDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine>

    fun removeDecorationToLines(text: CharSequence): Sequence<RsDocLine> =
        removeDecoration(RsDocLine.splitLines(text))

    fun removeDecoration(text: CharSequence): Sequence<String> =
        removeDecorationToLines(text).map { it.content }

    protected fun removeEolDecoration(decoratedLines: Sequence<RsDocLine>, infix: String = this.infix): Sequence<RsDocLine> {
        val lines = decoratedLines.map { it.trimStart().removePrefix(infix) }.iterator()
        val firstLineIndented = lines.nextOrNull() ?: return emptySequence()
        val firstLine = firstLineIndented.trimStart()
        val indent = firstLine.contentStartOffset - firstLineIndented.contentStartOffset

        return sequenceOf(firstLine) + lines.asSequence().map {
            it.dropWhileAtMost(indent) { it == ' ' }
        }
    }

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
            val newOffset = CharArrayUtil.shiftForward(text, contentStartOffset, contentEndOffset, " \t")
            return copy(contentStartOffset = newOffset, contentEndOffset = contentEndOffset)
        }

        fun startsWith(s: String): Boolean =
            CharArrayUtil.regionMatches(text, contentStartOffset, contentEndOffset, s)

        fun dropWhileAtMost(n: Int, predicate: (Char) -> Boolean): RsDocLine {
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

    protected fun removeBlockDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> {
        val ll = lines.toMutableList()
        return if (ll.asSequence().drop(1).all { it.trimStart().startsWith("*") }) {
            // Doing some patches we can "convert" block comment into eol one
            ll[0] = ll[0].removePrefix(prefix)
            ll[ll.lastIndex] = ll[ll.lastIndex].trimTrailingAsterisks()
            sequenceOf(ll[0]) + removeEolDecoration(ll.subList(1, ll.size).asSequence(), infix)
        } else {
            ll[0] = ll[0].removePrefix(prefix)
            ll[ll.lastIndex] = ll[ll.lastIndex].trimTrailingAsterisks()
            ll.asSequence()
        }
    }

    companion object {
        /**
         * Get [RsDocKind] of given doc comment token [IElementType].
         *
         * For the set of supported token types see [org.rust.lang.core.psi.RS_DOC_COMMENTS].
         *
         * @throws IllegalArgumentException when given token type is unsupported
         */
        fun of(tokenType: IElementType): RsDocKind = when (tokenType) {
            INNER_BLOCK_DOC_COMMENT -> InnerBlock
            OUTER_BLOCK_DOC_COMMENT -> OuterBlock
            INNER_EOL_DOC_COMMENT -> InnerEol
            OUTER_EOL_DOC_COMMENT -> OuterEol
            else -> throw IllegalArgumentException("unsupported token type")
        }
    }
}
