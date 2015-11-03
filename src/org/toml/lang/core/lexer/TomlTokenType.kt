package org.toml.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.toml.lang.TomlLanguage


public class TomlTokenType(debugName: String) : IElementType(debugName, TomlLanguage.INSTANCE)
