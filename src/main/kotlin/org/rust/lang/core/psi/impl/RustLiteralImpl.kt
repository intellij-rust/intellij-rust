package org.rust.lang.core.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.containers.ContainerUtil
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustTokenElementTypes

private val VALID_INTEGER_SUFFIXES = listOf("u8", "i8", "u16", "i16", "u32", "i32", "u64", "i64", "isize", "usize")
private val VALID_FLOAT_SUFFIXES = listOf("f32", "f64")

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
        get() = when (tokenType) {
            RustTokenElementTypes.INTEGER_LITERAL -> VALID_INTEGER_SUFFIXES
            RustTokenElementTypes.FLOAT_LITERAL   -> VALID_FLOAT_SUFFIXES
            else                                  -> ContainerUtil.emptyList()
        }

    override val hasPairedDelimiters: Boolean
        get() = throw UnsupportedOperationException("not implemented yet")

    override fun toString(): String = "RustLiteralImpl($tokenType)"
}
