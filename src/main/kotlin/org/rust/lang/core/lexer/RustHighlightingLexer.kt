package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.doc.lexer.RustDocHighlightingLexer

class RustHighlightingLexer : LayeredLexer(RustLexer()) {
    init {
        ESCAPABLE_LITERALS_TOKEN_SET.types.forEach {
            registerLayer(RustEscapesLexer.of(it), it)
        }

        registerLayer(RustDocHighlightingLexer(isBlock = true), INNER_BLOCK_DOC_COMMENT, OUTER_BLOCK_DOC_COMMENT)
        registerLayer(RustDocHighlightingLexer(isBlock = false), INNER_EOL_DOC_COMMENT, OUTER_EOL_DOC_COMMENT)
    }
}
