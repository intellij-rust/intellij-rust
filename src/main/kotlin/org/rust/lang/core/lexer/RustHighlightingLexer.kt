package org.rust.lang.core.lexer

import com.intellij.lexer.LayeredLexer
import org.rust.lang.core.lexer.RustTokenElementTypes.*

class RustHighlightingLexer : LayeredLexer(RustLexer()) {
    init {
        registerLayer(RustStringLiteralLexer.forByteLiterals(), BYTE_LITERAL)
        registerLayer(RustStringLiteralLexer.forCharLiterals(), CHAR_LITERAL)
        registerLayer(RustStringLiteralLexer.forByteStringLiterals(), BYTE_STRING_LITERAL)
        registerLayer(RustStringLiteralLexer.forStringLiterals(), STRING_LITERAL)
    }
}
