/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.tree.IElementType
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
            removeAttrDecoration(lines)
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

    fun removeDecoration(text: CharSequence): Sequence<CharSequence> =
        removeDecorationToLines(text).map { it.content }

    protected fun removeEolDecoration(decoratedLines: Sequence<RsDocLine>, infix: String = this.infix): Sequence<RsDocLine> {
        val lines = decoratedLines.map { it.trimStart().removePrefix(infix) }.iterator()
        val firstLineIndented = lines.nextOrNull() ?: return emptySequence()
        val firstLine = firstLineIndented.trimStart()
        val indent = firstLine.contentStartOffset - firstLineIndented.contentStartOffset

        return sequenceOf(firstLine) + lines.asSequence().map { line ->
            line.indentBy(indent)
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
            val minIndent = ll.asSequence().drop(1).map { it.leadingWhitespace() }.minOrNull()
            if (minIndent != null) {
                for (i in 1 until ll.size) {
                    ll[i] = ll[i].indentBy(minIndent)
                }
            }
            ll[ll.lastIndex] = ll[ll.lastIndex].trimTrailingAsterisks()
            ll.asSequence()
        }
    }

    // https://github.com/rust-lang/rust/blob/edeee915b1c52f97411e57ef6b1a8bd46548a37a/compiler/rustc_ast/src/util/comments.rs#L28
    protected fun removeAttrDecoration(linesSequence: Sequence<RsDocLine>): Sequence<RsDocLine> {
        /** Removes whitespace-only lines from the start/end of lines */
        fun doVerticalTrim(lines: List<RsDocLine>): List<RsDocLine> {
            var start = 0
            var end = lines.size

            // First line of all-stars should be omitted
            if (lines[0].content.all { it == '*' }) {
                start++
            }

            while (start < end && lines[start].content.isBlank()) {
                start++
            }

            // Like the first, a last line of all stars should be omitted
            // The first character is skipped by Rustc for some reason
            if (end > start && (lines[end - 1].content.isEmpty() || lines[end - 1].content.substring(1).all { it == '*' })) {
                end--
            }

            while (end > start && lines[end - 1].content.isBlank()) {
                end--
            }

            return lines.subList(start, end)
        }

        /** Calculates common indent before `*`, if possible */
        fun calculateCommonIndentBeforeAsterisk(lines: List<RsDocLine>): Int? {
            var indent = Int.MAX_VALUE
            var first = true

            for (line in lines) {
                for ((j, c) in line.content.withIndex()) {
                    if (j > indent || !"* \t".contains(c)) {
                        return null
                    }
                    if (c == '*') {
                        if (first) {
                            indent = j
                            first = false
                        } else if (indent != j) {
                            return null
                        }
                        break
                    }
                }
                if (indent >= line.contentLength) {
                    return null
                }
            }
            return indent
        }

        val lines = linesSequence.toList()
        if (lines.size <= 1) return lines.asSequence()
        val lines2 = doVerticalTrim(lines)
        val indent = calculateCommonIndentBeforeAsterisk(lines2)
        return removeEolDecoration(if (indent != null) {
            lines2.map { it.substring(indent + 1) }
        } else {
            lines2
        }.asSequence(), infix = "")
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
