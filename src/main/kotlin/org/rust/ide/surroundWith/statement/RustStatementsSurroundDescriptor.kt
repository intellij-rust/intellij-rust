package org.rust.ide.surroundWith.statement

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.surroundWith.expression.*
import org.rust.ide.surroundWith.statement.RustWithBlockSurrounder
import org.rust.ide.surroundWith.statement.RustWithForSurrounder
import org.rust.ide.surroundWith.statement.RustWithIfSurrounder
import org.rust.ide.surroundWith.statement.RustWithLoopSurrounder
import org.rust.lang.core.psi.util.findStatementsInRange

class RustStatementsSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<out PsiElement> {
        val stmts = findStatementsInRange(file, startOffset, endOffset)
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression")
        return stmts
    }

    override fun getSurrounders(): Array<out Surrounder> = SURROUNDERS

    override fun isExclusive() = false

    companion object {
        private val SURROUNDERS = arrayOf(
            RustWithBlockSurrounder(),
            RustWithLoopSurrounder(),
            RustWithWhileSurrounder(),
            RustWithIfSurrounder(),
            RustWithForSurrounder()
        )
    }
}
