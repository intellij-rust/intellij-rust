/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.inspections.import.AutoImportHintFix
import org.rust.ide.inspections.import.ImportCandidate
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffsetInParent
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling
import javax.swing.JComponent

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {

    var ignoreWithoutQuickFix: Boolean = true

    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitPath(path: RsPath) {
                if (path.parent is RsPath) return
                // Don't show unresolved reference error in attributes for now
                if (path.ancestorStrict<RsMetaItem>() != null) return
                val (basePath, candidates) = AutoImportFix.findApplicableContext(holder.project, path) ?: return
                if (candidates.isEmpty() && ignoreWithoutQuickFix) return

                val referenceName = basePath.identifier?.text
                // Don't highlight generic parameters
                val range = TextRange(
                    0,
                    path.typeArgumentList?.getPrevNonCommentSibling()?.endOffsetInParent ?: path.textLength
                )
                holder.registerProblem(path, candidates, referenceName, range)
            }

            override fun visitMethodCall(methodCall: RsMethodCall) {
                val (_, candidates) = AutoImportFix.findApplicableContext(holder.project, methodCall) ?: return

                if (candidates.isEmpty() && ignoreWithoutQuickFix) return

                val identifier = methodCall.identifier
                val referenceName = identifier.text
                val range = TextRange(0, identifier.textLength)
                holder.registerProblem(methodCall, candidates, referenceName, range)
            }
        }

    private fun RsProblemsHolder.registerProblem(
        element: RsElement,
        candidates: List<ImportCandidate>,
        referenceName: String?,
        range: TextRange
    ) {
        val description = if (referenceName == null) "Unresolved reference" else "Unresolved reference: `$referenceName`"
        var fix: LocalQuickFix? = null
        if (candidates.isNotEmpty()) {
            fix = if (RsCodeInsightSettings.getInstance().showImportPopup) {
                AutoImportHintFix(element, candidates[0].info.usePath, candidates.size > 1)
            } else {
                AutoImportFix(element)
            }
        }

        registerProblem(element, description, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            range, *listOfNotNull(fix).toTypedArray())
    }

    override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
        addCheckbox("Ignore unresolved references without quick fix", "ignoreWithoutQuickFix")
    }
}
