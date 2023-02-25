/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.ide.inspections.lints.RsLint
import org.rust.ide.inspections.lints.RsLintInspection
import org.rust.ide.utils.checkMatch.*
import org.rust.lang.core.psi.RsElementTypes.OR
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

class RsUnreachablePatternsInspection : RsLintInspection() {
    override fun getDisplayName(): String = "Unreachable patterns"

    override fun getLint(element: PsiElement): RsLint = RsLint.UnreachablePattern

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitMatchExpr(matchExpr: RsMatchExpr) {
            val exprType = matchExpr.expr?.type ?: return
            if (exprType.containsTyOfClass(TyUnknown::class.java)) return
            try {
                checkUnreachablePatterns(matchExpr, holder)
            } catch (todo: NotImplementedError) {
            } catch (e: CheckMatchException) {
            }
        }
    }

    private fun checkUnreachablePatterns(match: RsMatchExpr, holder: RsProblemsHolder) {
        val matrix = match.arms
            .calculateMatrix()
            .takeIf { it.isNotEmpty() && it.isWellTyped() }
            ?: return

        val armPats = match.arms.flatMap { it.patList }
        val seen = mutableListOf<List<Pattern>>()

        for ((i, patterns) in matrix.withIndex()) {
            val armPat = armPats[i]
            val useful = isUseful(seen, patterns, false, match.crateRoot, isTopLevel = true)
            if (!useful.isUseful) {
                val arm = armPat.ancestorStrict<RsMatchArm>() ?: return

                val fix = if (arm.patList.size == 1) {
                    /** If the arm consists of only one pattern, we can delete the whole arm */
                    SubstituteTextFix.delete("Remove unreachable match arm", match.containingFile, arm.rangeWithPrevSpace)
                } else {
                    /** Otherwise, delete only ` | <pat>` part from the arm */
                    val separatorRange = (armPat.getPrevNonCommentSibling() as? LeafPsiElement)
                        ?.takeIf { it.elementType == OR }
                        ?.rangeWithPrevSpace
                        ?: TextRange.EMPTY_RANGE

                    val range = armPat.rangeWithPrevSpace.union(separatorRange)
                    SubstituteTextFix.delete("Remove unreachable pattern", match.containingFile, range)
                }

                holder.registerLintProblem(armPat, "Unreachable pattern", fixes = listOf(fix))
            }

            /** If the arm is not guarded, we have "seen" the pattern */
            if (armPat.ancestorStrict<RsMatchArm>()?.matchArmGuard == null) {
                seen.add(patterns)
            }
        }
    }
}
