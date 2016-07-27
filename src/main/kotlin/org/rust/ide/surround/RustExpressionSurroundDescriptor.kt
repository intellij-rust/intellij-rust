package org.rust.ide.surround

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustExprElement

class RustExpressionSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<out PsiElement> {
        val expr = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, RustExprElement::class.java)
            ?: return PsiElement.EMPTY_ARRAY
        return arrayOf(expr)
    }

    override fun getSurrounders(): Array<out Surrounder> = SURROUNDERS

    override fun isExclusive() = false

    companion object {
        private val SURROUNDERS = arrayOf(
            RustDelimiterSurrounder("(", ")", "Surround with ()"),
            RustDelimiterSurrounder("{", "}", "Surround with {}")
        )
    }
}
