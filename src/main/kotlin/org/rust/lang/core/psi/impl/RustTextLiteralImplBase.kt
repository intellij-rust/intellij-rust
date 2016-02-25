package org.rust.lang.core.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.visitors.RustVisitorEx

abstract class RustTextLiteralImplBase(type: IElementType, text: CharSequence) : RustLiteral.Text(type, text) {
    override val possibleSuffixes: Collection<String>
        get() = emptyList()

    override val hasPairedQuotes: Boolean
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
