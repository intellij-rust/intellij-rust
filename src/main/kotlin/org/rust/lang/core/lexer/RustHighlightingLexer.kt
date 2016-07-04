package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.psi.RustTokenElementTypes.DOC_COMMENTS_TOKEN_SET
import org.rust.lang.core.psi.RustTokenElementTypes.ESCAPABLE_LITERALS_TOKEN_SET
import org.rust.lang.doc.lexer.RustDocHighlightingLexer
import org.rust.lang.doc.psi.RustDocKind

class RustHighlightingLexer : LayeredLexer(RustLexer()) {
    init {
        ESCAPABLE_LITERALS_TOKEN_SET.types.forEach {
            registerLayer(RustEscapesLexer.of(it), it)
        }

        DOC_COMMENTS_TOKEN_SET.types.forEach {
            registerLayer(RustDocHighlightingLexer(RustDocKind.of(it)), it)
        }
    }
}
