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

inline fun <T> Lexer.peekingFrame(action: () -> T): T {
    val pos = currentPosition
    val result = action()
    restore(pos)
    return result
}

//
// String extensions
//

// TODO(kudinkin): Sync with flex
fun String.isEOL(): Boolean =
    equals("\r") || equals("\n") || equals("\r\n")

// TODO(kudinkin): Sync with flex
fun String.containsEOL(): Boolean =
    contains("\r") || contains("\n") || contains("\r\n")

fun CharSequence.softSubSequence(startIndex: Int, endIndex: Int): CharSequence =
    subSequence(startIndex, Math.min(endIndex, length))
