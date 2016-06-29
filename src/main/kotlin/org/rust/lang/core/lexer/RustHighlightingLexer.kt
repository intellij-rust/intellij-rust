package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.psi.RustTokenElementTypes.DOC_COMMENTS_TOKEN_SET
import org.rust.lang.core.psi.RustTokenElementTypes.ESCAPABLE_LITERALS_TOKEN_SET
import org.rust.lang.doc.lexer.RustDocHighlightingLexer

class RustHighlightingLexer : LayeredLexer(RustLexer()) {
    init {
        ESCAPABLE_LITERALS_TOKEN_SET.types.forEach {
            registerLayer(RustEscapesLexer.of(it), it)
        }

        registerLayer(RustDocHighlightingLexer(), *(DOC_COMMENTS_TOKEN_SET.types))
    }
}
