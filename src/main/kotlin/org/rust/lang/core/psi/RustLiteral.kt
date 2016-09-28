package org.rust.lang.core.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

sealed class RustLiteral(type: IElementType, text: CharSequence) : LeafPsiElement(type, text) {
    /**
     * Get a list of possible suffixes for given literal type.
     */
    abstract val possibleSuffixes: Collection<String>

    abstract override fun toString(): String

    protected abstract fun computeOffsets(): Offsets

    val offsets: Offsets by lazy { computeOffsets() }

    /**
     * Get a fragment of the source which denotes the value of the literal as-is (without any escaping etc).
     */
    val valueString: String?
        get() = offsets.value?.substring(text)

    /**
     * Get literal suffix.
     */
    val suffix: String?
        get() = offsets.suffix?.substring(text)

    /**
     * Base class for numeric literals: integers and floats.
     */
    abstract class Number(type: IElementType, text: CharSequence) : RustLiteral(type, text) {
        abstract val valueAsLong: Long?
        abstract val valueAsDouble: Double?
        abstract val isInt: Boolean
        abstract val isFloat: Boolean
    }

    /**
     * Base class for character and string literals.
     */
    abstract class Text(type: IElementType, text: CharSequence) : RustLiteral(type, text) {
        abstract val value: String?
        abstract val hasUnpairedQuotes: Boolean
    }

    /**
     * Stores offsets of distinguishable parts of a literal.
     */
    data class Offsets(
        val prefix: TextRange? = null,
        val openDelim: TextRange? = null,
        val value: TextRange? = null,
        val closeDelim: TextRange? = null,
        val suffix: TextRange? = null
    ) {
        companion object {
            fun fromEndOffsets(prefixEnd: Int, openDelimEnd: Int, valueEnd: Int,
                               closeDelimEnd: Int, suffixEnd: Int): Offsets {
                val prefix = makeRange(0, prefixEnd)
                val openDelim = makeRange(prefixEnd, openDelimEnd)

                val value = makeRange(openDelimEnd, valueEnd) ?:
                    // empty value is still a value provided we have open delimiter
                    if (openDelim != null) TextRange.create(openDelimEnd, openDelimEnd) else null

                val closeDelim = makeRange(valueEnd, closeDelimEnd)
                val suffix = makeRange(closeDelimEnd, suffixEnd)

                return Offsets(
                    prefix = prefix, openDelim = openDelim, value = value,
                    closeDelim = closeDelim, suffix = suffix)
            }

            private fun makeRange(start: Int, end: Int): TextRange? = when {
                end - start > 0 -> TextRange(start, end)
                else -> null
            }
        }
    }
}
