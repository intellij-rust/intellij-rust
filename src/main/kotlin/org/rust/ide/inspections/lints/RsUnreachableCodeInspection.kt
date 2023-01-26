/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.stripWhitespace
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.rangeWithPrevSpace
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.controlFlowGraph
import org.rust.openapiext.document
import org.rust.stdext.mapToMutableList
import java.util.*

class RsUnreachableCodeInspection : RsLintInspection() {
    override fun getDisplayName(): String = "Unreachable code"

    override fun getLint(element: PsiElement): RsLint = RsLint.UnreachableCode

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitFunction2(func: RsFunction) {
            if (func.isDoctestInjection) return
            val controlFlowGraph = func.controlFlowGraph ?: return

            val elementsToReport = controlFlowGraph
                .unreachableElements
                .takeIf { it.isNotEmpty() }
                ?: return

            // Collect text ranges of unreachable elements and merge them in order to highlight
            // most enclosing ranges entirely, instead of highlighting each element separately
            val sortedRanges = elementsToReport
                .filter { it.isPhysical }
                .mapToMutableList { it.rangeWithPrevSpace }
                .apply { sortWith(Segment.BY_START_OFFSET_THEN_END_OFFSET) }
                .takeIf { it.isNotEmpty() }
                ?: return

            val mergedRanges = mergeRanges(sortedRanges)
            for (range in mergedRanges) {
                registerUnreachableProblem(holder, func, range)
            }
        }
    }

    /** Merges intersecting (including adjacent) text ranges into one */
    private fun mergeRanges(sortedRanges: List<TextRange>): Collection<TextRange> {
        val mergedRanges = ArrayDeque<TextRange>()
        mergedRanges.add(sortedRanges[0])
        for (range in sortedRanges.drop(1)) {
            val leftNeighbour = mergedRanges.peek()
            if (leftNeighbour.intersects(range)) {
                mergedRanges.pop()
                mergedRanges.push(leftNeighbour.union(range))
            } else {
                mergedRanges.push(range)
            }
        }
        return mergedRanges
    }

    private fun registerUnreachableProblem(holder: RsProblemsHolder, func: RsFunction, range: TextRange) {
        val chars = func.containingFile.document?.immutableCharSequence ?: return
        val strippedRangeInFunction = range.stripWhitespace(chars).shiftLeft(func.startOffset)

        holder.registerLintProblem(
            func,
            "Unreachable code",
            strippedRangeInFunction,
            RsLintHighlightingType.UNUSED_SYMBOL,
            listOf(SubstituteTextFix.delete("Remove unreachable code", func.containingFile, range))
        )
    }
}
