package org.rust.lang.core.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.rust.lang.RustLanguage

public open class RustTokenType(_: String) : IElementType(_, RustLanguage.INSTANCE) {

}