package org.rust.lang.core.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.visitors.RustVisitorEx

abstract class RustTextLiteralImplBase(type: IElementType, text: CharSequence) : RustLiteral.Text(type, text) {
    override val possibleSuffixes: Collection<String>
        get() = emptyList()

    override val hasUnpairedQuotes: Boolean
        get() = offsets.openDelim == null || offsets.closeDelim == null

    override fun accept(visitor: PsiElementVisitor) = when (visitor) {
        is RustVisitorEx -> visitor.visitTextLiteral(this)
        else -> super.accept(visitor)
    }

    protected fun locatePrefix(): Int {
        text.forEachIndexed { i, ch ->
            if (!ch.isLetter()) {
                return i
            }
        }
        return textLength
    }

    protected inline fun doLocate(start: Int, locator: (Int) -> Int): Int =
        if (start >= textLength) {
            start
        } else {
            locator(start)
        }
}
