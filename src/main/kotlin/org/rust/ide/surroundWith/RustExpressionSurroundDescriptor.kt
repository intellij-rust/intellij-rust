package org.rust.ide.surroundWith

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.util.findExpressionInRange

class RustExpressionSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<out PsiElement> {
        val expr = findExpressionInRange(file, startOffset, endOffset) ?: return emptyArray()
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression")
        return arrayOf(expr)
    }

    override fun getSurrounders(): Array<out Surrounder> = SURROUNDERS

    override fun isExclusive() = false

    companion object {
        private val SURROUNDERS = arrayOf(
            RustWithParenthesesSurrounder(),
            RustWithNotSurrounder()
        )
    }
}
