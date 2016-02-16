package org.rust.lang.utils

/**
 * Determines if the char is a Rust whitespace character.
 */
fun Char.isRustWhitespaceChar(): Boolean = equals(' ') || equals('\r') || equals('\n') || equals('\t')
