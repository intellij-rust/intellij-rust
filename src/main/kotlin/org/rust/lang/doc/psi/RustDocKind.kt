package org.rust.lang.doc.psi

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustTokenElementTypes.*

enum class RustDocKind {
    Attr,
    InnerBlock,
    OuterBlock,
    InnerEol,
    OuterEol;

    val isEol: Boolean
        get() = this == InnerEol || this == OuterEol

    val isBlock: Boolean
        get() = this == InnerBlock || this == OuterBlock

    companion object {
        /**
         * Get [RustDocKind] of given doc comment token [IElementType].
         *
         * For the set of supported token types see [].
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
