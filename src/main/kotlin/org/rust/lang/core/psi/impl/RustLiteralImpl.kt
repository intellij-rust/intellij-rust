package org.rust.lang.core.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral

class RustLiteralImpl(type: IElementType, text: CharSequence) : LeafPsiElement(type, text), RustLiteral {
    override val tokenType: IElementType
        get() = node.elementType

    override fun toString(): String = "RustLiteralImpl($tokenType)"
}
