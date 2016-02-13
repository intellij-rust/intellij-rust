package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import org.rust.lang.RustLanguage

open class RustTokenType(debugName: String) : IElementType(debugName, RustLanguage)

class RustKeywordTokenType(debugName: String) : RustTokenType(debugName)

class RustLiteralTokenType(debugName: String) : RustTokenType(debugName)
