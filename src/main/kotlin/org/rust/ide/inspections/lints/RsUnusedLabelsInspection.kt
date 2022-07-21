/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RemoveElementFix
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.searchReferences

/** Analogue of rustc's unused_labels. */
class RsUnusedLabelsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement) = RsLint.UnusedLabels

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitLabelDecl(o: RsLabelDecl) {
            val isLabelUsed = o.searchReferences().findFirst() != null
            if (!isLabelUsed) {
                val highlighting = RsLintHighlightingType.UNUSED_SYMBOL
                val description = RsBundle.message("inspection.UnusedLabels.description")
                val fixes = listOf(RemoveElementFix(o))
                holder.registerLintProblem(o, description, highlighting, fixes)
            }
        }
    }
}
