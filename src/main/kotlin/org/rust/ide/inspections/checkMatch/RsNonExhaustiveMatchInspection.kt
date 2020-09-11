/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.arms
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsNonExhaustiveMatchInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = "Non-exhaustive match"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMatchExpr(matchExpr: RsMatchExpr) {
            val exprType = matchExpr.expr?.type ?: return
            if (exprType.containsTyOfClass(TyUnknown::class.java)) return
            try {
                checkExhaustive(matchExpr, holder)
            } catch (todo: NotImplementedError) {
            } catch (e: CheckMatchException) {
            }
        }
    }

    private fun checkExhaustive(match: RsMatchExpr, holder: RsProblemsHolder) {
        val matchedExprType = match.expr?.type ?: return

        val matrix = match.arms
            .filter { it.matchArmGuard == null }
            .calculateMatrix()
            .takeIf { it.isWellTyped() }
            ?: return

        val wild = Pattern.wild(matchedExprType)
        val useful = isUseful(matrix, listOf(wild), true, match.crateRoot, isTopLevel = true)

        /** If `_` pattern is useful, the match is not exhaustive */
        if (useful is Usefulness.UsefulWithWitness) {
            val patterns = useful.witnesses.mapNotNull { it.patterns.singleOrNull() }
            RsDiagnostic.NonExhaustiveMatch(match, patterns).addToHolder(holder)
        }
    }
}
