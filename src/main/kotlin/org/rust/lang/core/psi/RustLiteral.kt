package org.rust.lang.core.psi

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

sealed class RustLiteral(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    abstract class Number(type: IElementType, text: CharSequence) : RustLiteral(type, text)
    abstract class Text(type: IElementType, text: CharSequence) : RustLiteral(type, text)

    val tokenType: IElementType
        get() = node.elementType

    abstract override fun toString(): String
}
