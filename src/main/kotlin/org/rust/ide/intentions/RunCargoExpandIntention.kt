/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.Cargo.Companion.checkNeedInstallCargoExpand
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.buildList

class RunCargoExpandIntention : RsElementBaseIntentionAction<RunCargoExpandIntention.Context>() {
    override fun getText() = "Show the result of macro expansion (cargo expand)"
    override fun getFamilyName() = text

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
        if (checkNeedInstallCargoExpand(cargoProject.project)) return

        val additionalArguments = buildList<String> {
            add("--color=always")
            add("--theme=GitHub")
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
