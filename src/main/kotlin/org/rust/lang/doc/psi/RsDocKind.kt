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

enum class RsDocKind {
    Attr {
        override val prefix: String = ""
        override val infix: String = ""

        override fun removeDecoration(lines: Sequence<String>): Sequence<String> =
            removeAttrDecoration(lines)
    },

    InnerBlock {
        override val prefix: String = "/*!"
        override val infix: String = "*"

        override fun removeDecoration(lines: Sequence<String>): Sequence<String> =
            removeBlockDecoration(lines)
    },

    OuterBlock {
        override val prefix: String = "/**"
        override val infix: String = "*"

        override fun removeDecoration(lines: Sequence<String>): Sequence<String> =
            removeBlockDecoration(lines)
    },

    InnerEol {
        override val infix: String = "//!"
        override val prefix: String = infix

        override fun removeDecoration(lines: Sequence<String>): Sequence<String> =
            removeEolDecoration(lines)
    },

    OuterEol {
        override val infix: String = "///"
        override val prefix: String = infix

        override fun removeDecoration(lines: Sequence<String>): Sequence<String> =
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
    abstract fun removeDecoration(lines: Sequence<String>): Sequence<String>

    protected fun removeEolDecoration(decoratedLines: Sequence<String>, infix: String = this.infix): Sequence<String> {
        val lines = decoratedLines.map { it.substringAfter(infix) }
        val firstLineIndented = lines.first()
        val firstLine = firstLineIndented.trimStart()
        val indent = firstLineIndented.length - firstLine.length

        return sequenceOf(firstLine) + lines.drop(1).map {
            it.dropWhileAtMost(indent) { it == ' ' }
        }
    }

    protected fun removeBlockDecoration(lines: Sequence<String>): Sequence<String> {
        val ll = lines.toMutableList()
        return if (lines.drop(1).all { it.trimStart(' ', '\t').startsWith("*") }) {
            // Doing some patches we can "convert" block comment into eol one
            ll[0] = ll[0].replaceFirst(prefix, " $infix ")
            ll[ll.lastIndex] = ll[ll.lastIndex].trimTrailingAsterisks()
            removeEolDecoration(ll.asSequence(), infix)
        } else {
            ll[0] = ll[0].removePrefix(prefix)
            ll[ll.lastIndex] = ll[ll.lastIndex].trimTrailingAsterisks()
            ll.asSequence()
        }
    }

    // https://github.com/rust-lang/rust/blob/edeee915b1c52f97411e57ef6b1a8bd46548a37a/compiler/rustc_ast/src/util/comments.rs#L28
    protected fun removeAttrDecoration(linesSequence: Sequence<String>): Sequence<String> {
        /** Removes whitespace-only lines from the start/end of lines */
        fun doVerticalTrim(lines: List<String>): List<String> {
            var start = 0
            var end = lines.size

            // First line of all-stars should be omitted
            if (lines[0].all { it == '*' }) {
                start++
            }

            while (start < end && lines[start].isBlank()) {
                start++
            }

            // Like the first, a last line of all stars should be omitted
            // The first character is skipped by Rustc for some reason
            if (end > start && (lines[end - 1].isEmpty() || lines[end - 1].substring(1).all { it == '*' })) {
                end--
            }

            while (end > start && lines[end - 1].isBlank()) {
                end--
            }

            return lines.subList(start, end)
        }

        /** Calculates common indent before `*`, if possible */
        fun calculateCommonIndentBeforeAsterisk(lines: List<String>): Int? {
            var indent = Int.MAX_VALUE
            var first = true

            for (line in lines) {
                for ((j, c) in line.withIndex()) {
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
                if (indent >= line.length) {
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

        private fun String.dropWhileAtMost(n: Int, predicate: (Char) -> Boolean): String {
            var i = n
            for (index in this.indices) {
                if (i-- <= 0 || !predicate(this[index])) {
                    return substring(index)
                }
            }
            return ""
        }

        /**
         * Get rid of trailing (pseudo-regexp): `[ ]+ [*]* * /`
         */
        private fun String.trimTrailingAsterisks(): String {
            if (length < 2) return this

            var i = lastIndex
            if (get(i - 1) == '*' && get(i) == '/') {
                i -= 2
                while (i >= 0 && get(i) == '*') i--
                while (i >= 0 && get(i) == ' ') i--
            }

            return substring(0, i + 1)
        }
    }
}
