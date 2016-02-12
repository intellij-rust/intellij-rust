package org.rust.lang.core.lexer

import com.intellij.psi.tree.IElementType
import org.rust.lang.RustLanguage

public open class RustTokenType(val debugName: String) : IElementType(debugName, RustLanguage) {}

public class RustKeywordTokenType(debugName: String) : RustTokenType(debugName) {}

public class RustLiteralTokenType(debugName: String) : RustTokenType(debugName) {}
