package org.rust.lang.doc.psi

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes.*

enum class RustDocKind {
    Attr {
        override val prefix: String = ""
        override val infix: String = ""

        override fun removeLineDecoration(line: String): String = line
    },

    InnerBlock {
        override val prefix: String = "/*!"
        override val infix: String = "*"

        // FIXME: This trimming is devastating
        // FIXME: Asterisk handling in block comments is just wrong
        override fun removeLineDecoration(line: String): String = line.substringAfter(infix).trim()
    },

    OuterBlock {
        override val prefix: String = "/**"
        override val infix: String = "*"

        // FIXME: This trimming is devastating
        // FIXME: Asterisk handling in block comments is just wrong
        override fun removeLineDecoration(line: String): String = line.substringAfter(infix).trim()
    },

    InnerEol {
        override val infix: String = "//!"
        override val prefix: String = infix

        // FIXME: This trimming is devastating
        override fun removeLineDecoration(line: String): String = line.substringAfter(infix).trim()
    },

    OuterEol {
        override val infix: String = "///"
        override val prefix: String = infix

        // FIXME: This trimming is devastating
        override fun removeLineDecoration(line: String): String = line.substringAfter(infix).trim()
    };

    abstract val prefix: String
    abstract val infix: String

    val isBlock: Boolean
        get() = this == InnerBlock || this == OuterBlock

    abstract fun removeLineDecoration(line: String): String

    companion object {
        /**
         * Get [RustDocKind] of given doc comment token [IElementType].
         *
         * For the set of supported token types see [DOC_COMMENTS_TOKEN_SET].
         *
         * @throws IllegalArgumentException when given token type is unsupported
         */
        fun of(tokenType: IElementType): RustDocKind = when (tokenType) {
            INNER_BLOCK_DOC_COMMENT -> InnerBlock
            OUTER_BLOCK_DOC_COMMENT -> OuterBlock
            INNER_EOL_DOC_COMMENT   -> InnerEol
            OUTER_EOL_DOC_COMMENT   -> OuterEol
            else                    -> throw IllegalArgumentException("unsupported token type")
        }
    }
}
