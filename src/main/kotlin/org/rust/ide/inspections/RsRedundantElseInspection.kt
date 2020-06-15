/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RsCondition
import org.rust.lang.core.psi.RsElseBranch
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.isIrrefutable
import org.rust.lang.core.psi.ext.leftSiblings
import org.rust.lang.core.psi.ext.patList
import org.rust.lang.core.psi.ext.rangeWithPrevSpace
import org.rust.lang.core.types.consts.asBool
import org.rust.lang.utils.evaluation.evaluate

/**
 * Detects redundant `else` statements preceded by an irrefutable pattern.
 * Quick fix: Remove `else`
 */
class RsRedundantElseInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Redundant else"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitElseBranch(expr: RsElseBranch) {
                if (!expr.isRedundant) return

                val elseExpr = expr.`else`
                holder.registerProblem(
                    expr,
                    elseExpr.textRangeInParent,
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
        private val RsCondition.isRedundant: Boolean
            get() {
                return patList?.all { pat -> pat.isIrrefutable } ?: (this.expr.evaluate().asBool() ?: false)
            }
    }
}
