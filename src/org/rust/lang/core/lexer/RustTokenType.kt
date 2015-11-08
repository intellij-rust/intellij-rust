package org.rust.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.rust.lang.RustLanguage

public open class RustTokenType(val s: String) : IElementType(s, RustLanguage.INSTANCE) {

}