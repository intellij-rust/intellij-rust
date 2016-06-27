package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.psi.RustTokenElementTypes.*

class RustHighlightingLexer : LayeredLexer(RustLexer()) {
    init {
        ESCAPABLE_LITERALS_TOKEN_SET.types.forEach {
            registerLayer(RustEscapesLexer.of(it), it)
        }

        registerLayer(RustDocLexer(), INNER_DOC_COMMENT, OUTER_DOC_COMMENT)
    }
}
