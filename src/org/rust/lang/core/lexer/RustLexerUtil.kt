package org.rust.lang.core.lexer

//
// String extensions
//

public fun String.isEOL() : Boolean {
    // TODO(kudinkin): Sync with flex
    return equals("\r")
        || equals("\n")
        || equals("\r\n")
}

public fun String.containsEOL() : Boolean {
    // TODO(kudinkin): Sync with flex
    return contains("\r")
        || contains("\n")
        || contains("\r\n")
}