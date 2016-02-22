package org.rust.lang.core.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType

fun CharSequence.tokenize(lexer: Lexer): Sequence<Pair<IElementType, String>> =
    generateSequence({
        lexer.start(this)
        lexer.tokenType?.to(lexer.tokenText)
    }, {
        lexer.advance()
        lexer.tokenType?.to(lexer.tokenText)
    })

//
// String extensions
//

fun String.isEOL(): Boolean {
    // TODO(kudinkin): Sync with flex
    return equals("\r")
        || equals("\n")
        || equals("\r\n")
}

fun String.containsEOL(): Boolean {
    // TODO(kudinkin): Sync with flex
    return contains("\r")
        || contains("\n")
        || contains("\r\n")
}
