/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.findExpressionInRange

class RsExpressionSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<out PsiElement> {
        val expr = findExpressionInRange(file, startOffset, endOffset) ?: return emptyArray()
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression")
        return arrayOf(expr)
    }

    override fun getSurrounders(): Array<out Surrounder> = SURROUNDERS

    override fun isExclusive() = false

    companion object {
        private val SURROUNDERS = arrayOf(
            RsWithParenthesesSurrounder(),
            RsWithNotSurrounder(),
            RsWithIfExpSurrounder(),
            RsWithWhileExpSurrounder()
        )
    }
}
