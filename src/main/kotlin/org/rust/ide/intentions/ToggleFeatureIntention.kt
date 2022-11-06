/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.RsPsiPattern.anyCfgCondition
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.ext.*

class ToggleFeatureIntention : RsElementBaseIntentionAction<ToggleFeatureIntention.Context>(), HighPriorityAction {
    data class Context(val featureName: String, val element: RsElement)

    override fun getFamilyName() = RsBundle.message("intention.Rust.ToggleFeatureIntention.family.name")

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val featureMetaItem = element.ancestorOrSelf<RsMetaItem>()
            ?.takeIf { m -> m.name == "feature" && m.ancestors.any { anyCfgCondition.accepts(it) } }
            ?: return null

        val context = featureMetaItem.litExpr ?: return null
        val featureName = context.stringValue ?: return null
        val isEnabled = isFeatureEnabled(context, featureName) ?: return null

        text = if (isEnabled) {
            RsBundle.message("intention.Rust.ToggleFeatureIntention.disable", featureName)
        } else {
            RsBundle.message("intention.Rust.ToggleFeatureIntention.enable", featureName)
        }

        return Context(featureName, context)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val element = ctx.element
        val cargoProject = element.cargoProject ?: return
        val pkg = element.containingCargoPackage ?: return

        val feature = pkg.features.find { it.name == ctx.featureName } ?: return
        val state = pkg.featureState[ctx.featureName] ?: return
        project.cargoProjects.modifyFeatures(cargoProject, setOf(feature), !state)
    }

    // No intention preview because it doesn't modify any code
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}

private fun isFeatureEnabled(element: RsElement, name: String): Boolean? {
    val pkg = element.containingCargoPackage ?: return null
    if (pkg.origin != PackageOrigin.WORKSPACE) return null

    val state = pkg.featureState[name] ?: return null
    return state.isEnabled
}
