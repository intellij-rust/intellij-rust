/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import org.rust.cargo.project.configurable.RsProjectConfigurable
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.tools.Cargo.Companion.checkNeedInstallCargoExpand
import org.rust.cargo.toolchain.tools.rustc
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.openapiext.isUnderDarkTheme
import org.rust.openapiext.showSettingsDialog
import org.rust.stdext.buildList

class RunCargoExpandIntention : RsElementBaseIntentionAction<RunCargoExpandIntention.Context>(), LowPriorityAction {
    override fun getText(): String = "Show the result of macro expansion (cargo expand)"
    override fun getFamilyName(): String = text

    override fun startInWriteAction(): Boolean = false

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

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (cargoProject, cargoTarget, crateRelativePath) = ctx

        if (!checkIsNightlyToolchain(cargoProject.project, cargoTarget)) return
        if (checkNeedInstallCargoExpand(cargoProject.project)) return

        val theme = if (isUnderDarkTheme) "Dracula" else "GitHub"
        val additionalArguments = buildList<String> {
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

        private fun checkIsNightlyToolchain(project: Project, cargoTarget: CargoWorkspace.Target): Boolean {
            val channel = project.computeWithCancelableProgress("Fetching rustc version...") {
                project.toolchain?.rustc()?.queryVersion(cargoTarget.pkg.rootDirectory)?.channel
            }
            if (channel == RustChannel.NIGHTLY) return true

            val option = Messages.showDialog(
                project,
                "Cargo Expand is available only with nightly toolchain",
                "Unable to Run Cargo Expand",
                arrayOf("Configure"),
                Messages.OK,
                Messages.getErrorIcon()
            )
            if (option == Messages.OK) {
                project.showSettingsDialog<RsProjectConfigurable>()
            }

            return false
        }
    }
}
