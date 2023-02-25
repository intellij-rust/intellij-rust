/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.CompilerFeature.Companion.LET_ELSE
import org.rust.lang.core.FeatureAvailability
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

/**
 * Detects redundant `else` statements preceded by an irrefutable pattern.
 * Quick fix: Remove `else`
 *
 * See also [RsConstantConditionIfInspection].
 */
class RsRedundantElseInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Redundant else"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {

            override fun visitElseBranch(expr: RsElseBranch) {
                if (expr.isRedundant) registerProblem(expr, expr.`else`.textRangeInParent)
            }

            override fun visitLetElseBranch(expr: RsLetElseBranch) {
                if (LET_ELSE.availability(expr) != FeatureAvailability.AVAILABLE) return
                if (expr.isRedundant) registerProblem(expr, expr.`else`.textRangeInParent)
            }

            private fun registerProblem(expr: RsElement, textRange: TextRange) {
                holder.registerProblem(
                    expr,
                    textRange,
                    "Redundant `else`",
                    SubstituteTextFix.delete(
                        "Remove `else`",
                        expr.containingFile,
                        expr.rangeWithPrevSpace
                    )
                )
            }
        }

    companion object {
        private val RsElseBranch.isRedundant: Boolean
            get() {
                val set = mutableSetOf<RsCondition>()
                var candidate: PsiElement = this

                while (candidate is RsElseBranch || candidate is RsIfExpr) {
                    candidate.leftSiblings.filterIsInstance<RsCondition>().forEach { set.add(it) }
                    candidate = candidate.parent
                }

                return set.any { it.isRedundant }
            }

        private val RsLetElseBranch.isRedundant: Boolean
            get() = (parent as? RsLetDecl)?.pat?.isIrrefutable == true

        private val RsCondition.isRedundant: Boolean
            get() {
                val patList = (expr as? RsLetExpr)?.patList ?: return false
                return patList.all { pat -> pat.isIrrefutable }
            }
    }
}
