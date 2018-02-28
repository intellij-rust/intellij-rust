/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.fixes.import.AutoImportFix
import org.rust.ide.inspections.fixes.import.AutoImportHintFix
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisitor

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {

    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(path: RsPath) {
                if (path.parent is RsPath) return
                // TODO: add inspection option to register all problems
                val (basePath, candidates) = AutoImportFix.findApplicableContext(holder.project, path) ?: return
                val identifier = basePath.identifier?.text
                val description = if (identifier == null) "Unresolved reference" else "Unresolved reference: `$identifier`"
                val fix: LocalQuickFix = if (RsCodeInsightSettings.getInstance().showImportPopup) {
                    AutoImportHintFix(path, candidates[0].info.usePath, candidates.size > 1)
                } else {
                    AutoImportFix(path)
                }
                holder.registerProblem(path, description, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, fix)
            }
        }
}
