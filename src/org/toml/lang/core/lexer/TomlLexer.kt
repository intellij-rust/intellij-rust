package org.toml.lang.core.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

/**
 * @author Aleksey.Kladov
 */
public class TomlLexer : FlexAdapter(_TomlLexer(null as Reader?))
