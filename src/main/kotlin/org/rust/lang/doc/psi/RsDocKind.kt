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
            lines // nothing to do here
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
        // Doing some patches we can "convert" block comment into eol one
        val ll = lines.toMutableList()
        ll[0] = ll[0].replaceFirst(prefix, " $infix ")
        ll[ll.lastIndex] = ll[ll.lastIndex].trimTrailingAsterisks()
        return removeEolDecoration(ll.asSequence(), infix)
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
         * Get rid of trailing (pseudo-regexp): [ ]+ [*]* * /
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
