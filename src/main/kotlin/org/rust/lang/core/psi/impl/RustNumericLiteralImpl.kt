package org.rust.lang.core.psi.impl

import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustLiteralTokenType

class RustNumericLiteralImpl(type: IElementType, text: CharSequence) : RustLiteral.Number(type, text) {
    override fun toString(): String = "RustNumericLiteralImpl($tokenType)"

    companion object {
        @JvmStatic fun createTokenType(debugName: String): RustLiteralTokenType =
            RustLiteralTokenType(debugName, ::RustNumericLiteralImpl)
    }
}
