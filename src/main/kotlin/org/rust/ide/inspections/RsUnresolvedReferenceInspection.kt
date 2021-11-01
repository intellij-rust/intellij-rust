/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.inspections.fixes.QualifyPathFix
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.inspections.import.AutoImportHintFix
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.ImportCandidateBase
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import javax.swing.JComponent

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {

    var ignoreWithoutQuickFix: Boolean = true

    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {

            override fun visitPath(path: RsPath) {
                if (path.reference == null) return

                val rootPathParent = path.rootPath().parent
                if (rootPathParent is RsMetaItem) {
                    if (!rootPathParent.isMacroCall || !ProcMacroApplicationService.isEnabled()) return
                }

                val isPathUnresolved = path.resolveStatus != PathResolveStatus.RESOLVED
                val qualifier = path.qualifier

                val context = when {
                    qualifier == null && isPathUnresolved -> AutoImportFix.findApplicableContext(holder.project, path)
                    qualifier != null && isPathUnresolved -> {
                        // There is not sense to highlight path as unresolved
                        // if qualifier cannot be resolved as well
                        if (qualifier.resolveStatus != PathResolveStatus.RESOLVED) return
                        null
                    }
                    // Despite the fact that path is (multi)resolved by our resolve engine, it can be unresolved from
                    // the view of the rust compiler. Specifically we resolve associated items even if corresponding
                    // trait is not in the scope, so here we suggest importing such traits
                    (qualifier != null || path.typeQual != null) && !isPathUnresolved ->
                        AutoImportFix.findApplicableContextForAssocItemPath(holder.project, path)
                    else -> null
                }

                if (isPathUnresolved || context != null) {
                    holder.registerProblem(path, context)
                }
            }

            override fun visitMethodCall(methodCall: RsMethodCall) {
                val isMethodResolved = methodCall.reference.multiResolve().isNotEmpty()
                val context = AutoImportFix.findApplicableContext(holder.project, methodCall)

                if (!isMethodResolved || context != null) {
                    holder.registerProblem(methodCall, context)
                }
            }

            override fun visitExternCrateItem(externCrate: RsExternCrateItem) {
                if (externCrate.reference.multiResolve().isEmpty() &&
                    externCrate.containingCrate?.origin == PackageOrigin.WORKSPACE) {
                    RsDiagnostic.CrateNotFoundError(externCrate.referenceNameElement, externCrate.referenceName)
                        .addToHolder(holder)
                }
            }
        }

    private fun RsProblemsHolder.registerProblem(
        element: RsReferenceElement,
        context: AutoImportFix.Context?
    ) {
        val candidates = context?.candidates
        if (candidates.isNullOrEmpty() && ignoreWithoutQuickFix) return

        val referenceName = element.referenceName
        val description = if (referenceName == null) "Unresolved reference" else "Unresolved reference: `$referenceName`"
        val fixes = createQuickFixes(candidates, element, context)

        val highlightedElement = element.referenceNameElement ?: element
        registerProblem(
            highlightedElement,
            description,
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            *fixes.toTypedArray()
        )
    }

    override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
        addCheckbox("Ignore unresolved references without quick fix", "ignoreWithoutQuickFix")
    }
}

private fun createQuickFixes(
    candidates: List<ImportCandidateBase>?,
    element: RsReferenceElement,
    context: AutoImportFix.Context?
): List<LocalQuickFix> {
    if (context == null) return emptyList()

    val fixes = mutableListOf<LocalQuickFix>()
    if (candidates != null && candidates.isNotEmpty()) {
        val importFix = if (RsCodeInsightSettings.getInstance().showImportPopup) {
            AutoImportHintFix(element, context.type, candidates[0].info.usePath, candidates.size > 1)
        } else {
            AutoImportFix(element, context.type)
        }
        fixes.add(importFix)

        if (element is RsPath && context.type == AutoImportFix.Type.GENERAL_PATH && candidates.size == 1) {
            fixes.add(QualifyPathFix(element, candidates[0].info))
        }
    }
    return fixes
}
