package org.rust.lang.core.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral

class RustLiteralImpl(type: IElementType, text: CharSequence) : LeafPsiElement(type, text), RustLiteral {
    override val tokenType: IElementType
        get() = node.elementType

    override val value: Any?
        get() = throw UnsupportedOperationException("not implemented yet")

    override val valueString: String
        get() = throw UnsupportedOperationException("not implemented yet")

    override val suffix: String
        get() = throw UnsupportedOperationException("not implemented yet")

    override val possibleSuffixes: Collection<String>
        get() = throw UnsupportedOperationException("not implemented yet")

    override val hasPairedDelimiters: Boolean
        get() = throw UnsupportedOperationException("not implemented yet")

    override fun toString(): String = "RustLiteralImpl($tokenType)"
}
