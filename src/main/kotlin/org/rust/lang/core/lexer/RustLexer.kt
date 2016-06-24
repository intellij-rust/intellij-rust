package org.rust.lang.core.lexer

import com.intellij.lexer.FlexAdapter

class RustLexer : RustDocMergingLexerAdapter(FlexAdapter(_RustLexer()))
