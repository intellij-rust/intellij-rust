/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapiext.isUnitTestMode
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.cargo.toolchain.Rustfmt
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.openapiext.saveAllDocumentsAsTheyAre

class RustfmtCargoProjectAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (cargoProject, rustfmt) = getContext(e) ?: return
        saveAllDocumentsAsTheyAre(reformatLater = false)
        try {
            if (checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) return
            rustfmt.reformatCargoProject(cargoProject)
            val rootDir = cargoProject.rootDir ?: return
            // We want to refresh file synchronously only in unit test to get new text right after `reformat` call
            VfsUtil.markDirtyAndRefresh(!isUnitTestMode, true, true, rootDir)
        } catch (e: ExecutionException) {
            // Just easy way to know that something wrong happened
            if (isUnitTestMode) throw e
        }
    }

    private fun getContext(e: AnActionEvent): Pair<CargoProject, Rustfmt>? {
        val cargoProject = getAppropriateCargoProject(e.dataContext) ?: return null
        val rustfmt = cargoProject.project.toolchain?.rustfmt() ?: return null
        return Pair(cargoProject, rustfmt)
    }
}
