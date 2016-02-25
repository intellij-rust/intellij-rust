package org.rust.lang.core.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

sealed class RustLiteral(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    /**
     * Get literal value as a Java object.
     */
    abstract val value: Any?

    /**
     * Get a list of possible suffixes for given literal type.
     */
    abstract val possibleSuffixes: Collection<String>

    abstract override fun toString(): String

    protected abstract fun computeMetadata(): Metadata

    val metadata: Metadata by lazy { computeMetadata() }

    /**
     * Literal token type.
     */
    val tokenType: IElementType
        get() = node.elementType

    /**
     * Get a fragment of the source which denotes the value of the literal as-is (without any escaping etc).
     */
    val valueString: String?
        get() = metadata.value?.substring(text)

    /**
     * Get literal suffix.
     */
    val suffix: String?
        get() = metadata.suffix?.substring(text)

    override fun accept(visitor: PsiElementVisitor) = when (visitor) {
        is RustVisitorEx -> visitor.visitLiteral(this)
        else             -> super.accept(visitor)
    }

    /**
     * Base class for numeric literals: integers and floats.
     */
    abstract class Number(type: IElementType, text: CharSequence) : RustLiteral(type, text)

    /**
     * Base class for character and string literals.
     */
    abstract class Text(type: IElementType, text: CharSequence) : RustLiteral(type, text) {
        override abstract val value: String?

        override val possibleSuffixes: Collection<String>
            get() = emptyList()

        val hasPairedQuotes: Boolean
            get() = metadata.openDelim != null && metadata.closeDelim != null

        override fun computeMetadata(): Metadata {
            val prefixEnd = checkBounds(0) { locatePrefix() }
            val openDelimEnd = checkBounds(prefixEnd) { locateOpenDelim(prefixEnd) }
            val valueEnd = checkBounds(openDelimEnd) { locateValue(openDelimEnd) }
            val closeDelimEnd = checkBounds(valueEnd) { locateCloseDelim(valueEnd) }
            return Metadata.fromEndOffsets(prefixEnd, openDelimEnd, valueEnd, closeDelimEnd, textLength)
        }

        protected abstract fun locateOpenDelim(start: Int): Int
        protected abstract fun locateValue(start: Int): Int
        protected abstract fun locateCloseDelim(start: Int): Int

        override fun accept(visitor: PsiElementVisitor) = when (visitor) {
            is RustVisitorEx -> visitor.visitTextLiteral(this)
            else             -> super.accept(visitor)
        }

        private fun locatePrefix(): Int {
            text.forEachIndexed { i, ch ->
                if (!ch.isLetter()) {
                    return i
                }
            }
            return textLength
        }

        private fun checkBounds(start: Int, locator: () -> Int): Int =
            if (start >= textLength) {
                start
            } else {
                locator()
            }
    }

    /**
     * Stores offsets of distinguishable parts of a literal.
     */
    data class Metadata(val prefix: TextRange? = null,
                        val openDelim: TextRange? = null,
                        val value: TextRange? = null,
                        val closeDelim: TextRange? = null,
                        val suffix: TextRange? = null) {
        companion object {
            fun fromEndOffsets(prefixEnd: Int, openDelimEnd: Int, valueEnd: Int,
                               closeDelimEnd: Int, suffixEnd: Int): Metadata =
                Metadata(
                    prefix = makeRange(0, prefixEnd),
                    openDelim = makeRange(prefixEnd, openDelimEnd),
                    value = makeRange(openDelimEnd, valueEnd),
                    closeDelim = makeRange(valueEnd, closeDelimEnd),
                    suffix = makeRange(closeDelimEnd, suffixEnd))

            private fun makeRange(start: Int, end: Int): TextRange? = when {
                end - start > 0 -> TextRange(start, end)
                else            -> null
            }
        }
    }
}
