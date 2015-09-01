package org.rust.lang.core.lexer

import com.intellij.lexer.FlexAdapter
import org.rust.lang.core.lexer.gen._RustLexer

public class RustLexer : FlexAdapter(_RustLexer()) {}