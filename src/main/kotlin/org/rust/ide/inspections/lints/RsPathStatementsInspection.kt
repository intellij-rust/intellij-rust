/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RemoveElementFix
import org.rust.ide.fixes.SubstituteTextFix
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.isTailStmt
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.needsDrop
import org.rust.lang.core.types.type
import org.rust.lang.utils.evaluation.ThreeValuedLogic

// TODO: Future improvements: https://github.com/intellij-rust/intellij-rust/issues/9555
//  The inspection is currently disabled by default.
/** Analogue of https://doc.rust-lang.org/rustc/lints/listing/warn-by-default.html#path-statements */
class RsPathStatementsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.PathStatements

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitExprStmt(exprStmt: RsExprStmt) {
            super.visitExprStmt(exprStmt)

            val expr = exprStmt.expr
            if (expr is RsPathExpr && !exprStmt.isTailStmt) {
                val highlighting = RsLintHighlightingType.WEAK_WARNING
                val (description, fixes) = when (expr.implLookup.needsDrop(expr.type, expr)) {
                    ThreeValuedLogic.True ->
                        RsBundle.message("inspection.PathStatementsInspection.description.drops.value") to
                            listOf(SubstituteTextFix.replace(RsBundle.message("intention.name.use.drop.to.clarify.intent.drop", expr.text), expr.containingFile, expr.textRange, "drop(${expr.text})"))

                    ThreeValuedLogic.False ->
                        RsBundle.message("inspection.PathStatementsInspection.description.no.effect") to
                            listOf(RemoveElementFix(exprStmt))

                    ThreeValuedLogic.Unknown -> return
                }
                holder.registerLintProblem(exprStmt, description, highlighting, fixes)
            }
        }
    }
}
