/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.tools.Cargo.Companion.checkNeedInstallCargoExpand
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.isUnderDarkTheme
import org.rust.stdext.buildList

class RunCargoExpandIntention : RsElementBaseIntentionAction<RunCargoExpandIntention.Context>(), LowPriorityAction {
    override fun getText(): String = RsBundle.message("intention.name.show.result.macro.expansion.cargo.expand")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL
    override val functionLikeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val cargoProject: CargoProject,
        val cargoTarget: CargoWorkspace.Target,
        val crateRelativePath: String
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val mod = element.ancestorStrict<RsItemsOwner>() as? RsMod ?: return null
        val cargoProject = mod.cargoProject ?: return null
        val cargoTarget = mod.containingCargoTarget ?: return null
        val crateRelativePath = mod.crateRelativePath ?: return null
        return Context(cargoProject, cargoTarget, crateRelativePath)
    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (cargoProject, cargoTarget, crateRelativePath) = ctx
        if (checkNeedInstallCargoExpand(cargoProject.project)) return

        val theme = if (isUnderDarkTheme) "Dracula" else "GitHub"
        val additionalArguments = buildList {
            add("--color=always")
            add("--theme=$theme")
            add("--tests")
            if (crateRelativePath.isNotEmpty()) {
                add(crateRelativePath.removePrefix(PATH_SEPARATOR))
            }
        }

        CargoCommandLine.forTarget(
            cargoTarget,
            "expand",
            additionalArguments,
            usePackageOption = false
        ).run(cargoProject, "Expand ${cargoTarget.normName}$crateRelativePath")
    }

    companion object {
        private const val PATH_SEPARATOR: String = "::"
    }
}
