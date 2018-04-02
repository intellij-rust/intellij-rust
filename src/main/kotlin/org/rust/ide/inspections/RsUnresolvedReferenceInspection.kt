/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.inspections.import.AutoImportHintFix
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisitor
import javax.swing.JComponent

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {

    var ignoreWithoutQuickFix: Boolean = true

    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitPath(path: RsPath) {
                if (path.parent is RsPath) return
                val (basePath, candidates) = AutoImportFix.findApplicableContext(holder.project, path) ?: return
                if (candidates.isEmpty() && ignoreWithoutQuickFix) return

                val identifier = basePath.identifier?.text
                val description = if (identifier == null) "Unresolved reference" else "Unresolved reference: `$identifier`"
                var fix: LocalQuickFix? = null
                if (candidates.isNotEmpty()) {
                    fix = if (RsCodeInsightSettings.getInstance().showImportPopup) {
                        AutoImportHintFix(path, candidates[0].info.usePath, candidates.size > 1)
                    } else {
                        AutoImportFix(path)
                    }
                }

                // Don't highlight generic parameters
                val range = TextRange(0, path.typeArgumentList?.startOffsetInParent ?: path.textLength)
                holder.registerProblem(path, description,
                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, range, *listOfNotNull(fix).toTypedArray())
            }
        }

    override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
        addCheckbox("Ignore unresolved references without quick fix", "ignoreWithoutQuickFix")
    }
}
