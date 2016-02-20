package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType

class RustStringLiteralImpl(type: IElementType, text: CharSequence) : RustLiteral.Text(type, text) {
    override fun toString(): String = "RustStringLiteralImpl($tokenType)"

    companion object {
        @JvmStatic fun createTokenType(debugName: String): RustLiteralTokenType =
            RustLiteralTokenType(debugName, ::RustStringLiteralImpl)
    }
}
