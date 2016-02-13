package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.psi.RustTokenElementTypes.*

class RustHighlightingLexer : LayeredLexer(RustLexer()) {
    init {
        registerLayer(RustEscapesLexer.forByteLiterals(), BYTE_LITERAL)
        registerLayer(RustEscapesLexer.forCharLiterals(), CHAR_LITERAL)
        registerLayer(RustEscapesLexer.forByteStringLiterals(), BYTE_STRING_LITERAL)
        registerLayer(RustEscapesLexer.forStringLiterals(), STRING_LITERAL)
    }
}
