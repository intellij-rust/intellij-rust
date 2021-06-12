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
import kotlin.math.min

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
    val suffix: String get() = if (isBlock) "*/" else ""

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
        removeDecorationToLines(text).mapNotNull { if (it.isRemoved) null else it.content }

    protected fun removeEolDecoration(decoratedLines: Sequence<RsDocLine>, infix: String = this.infix): Sequence<RsDocLine> {
        return removeCommonIndent(decoratedLines.map { it.trimStart().removePrefix(infix) })
    }

    // https://github.com/rust-lang/rust/blob/b84ffce1aa806d1d53c2588fa/src/librustdoc/passes/unindent_comments.rs#L68
    protected fun removeCommonIndent(decoratedLines: Sequence<RsDocLine>): Sequence<RsDocLine> {
        val lines = decoratedLines.toList()

        val minIndent = lines.fold(Int.MAX_VALUE) { minIndent, line ->
            if (line.isRemoved || line.content.isBlank()) {
                minIndent
            } else {
                min(minIndent, line.countStartWhitespace())
            }
        }

        return lines.asSequence().map { line ->
            line.substring(min(minIndent, line.contentLength))
        }
    }

    protected fun removeBlockDecoration(lines: Sequence<RsDocLine>): Sequence<RsDocLine> {
        val lines2 = lines.toMutableList()
        if (lines2.isEmpty()) return emptySequence()
        lines2[0] = lines2[0].removePrefix(prefix)
        lines2[lines2.lastIndex] = lines2[lines2.lastIndex].removeSuffix(suffix)
        return removeAttrDecoration(lines2.asSequence())
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

            val lines2 = lines.toMutableList()

            for (i in 0 until start) {
                lines2[i] = lines2[i].markRemoved()
            }

            for (i in end until lines.size) {
                lines2[i] = lines2[i].markRemoved()
            }

            return lines2
        }

        /** Calculates common indent before `*`, if possible */
        fun calculateCommonIndentBeforeAsterisk(lines: List<RsDocLine>): Int? {
            var indent = Int.MAX_VALUE
            var first = true

            for (line in lines) {
                if (line.isRemoved) continue
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
        return removeCommonIndent(if (indent != null) {
            lines2.map { it.substring(min(indent + 1, it.contentLength)) }
        } else {
            lines2
        }.asSequence())
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
