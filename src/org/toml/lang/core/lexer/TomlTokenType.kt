package org.toml.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.toml.lang.TomlLanguage


/**
 * @author Aleksey.Kladov
 */
public class TomlTokenType(debugName: String) : IElementType(debugName, TomlLanguage.INSTANCE)
