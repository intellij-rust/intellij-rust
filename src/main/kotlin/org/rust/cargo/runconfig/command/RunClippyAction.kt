/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.getAppropriateCargoProject
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.run
import org.rust.ide.actions.InstallComponentAction
import org.rust.ide.notifications.showBalloon

class RunClippyAction : RunCargoCommandActionBase(CargoIcons.CLIPPY) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cargoProject = getAppropriateCargoProject(e.dataContext) ?: return
        val projectDirectory = cargoProject.manifest.parent ?: return

        // We don't want to install Clippy if:
        // 1. it is already installed
        // 2. we don't have Rustup
        // 3. Rustup doesn't have Clippy component
        val needInstallClippy = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable<Boolean, ExecutionException>{
                val rustup = project.toolchain?.rustup(projectDirectory) ?: return@ThrowableComputable false
                val (_, isClippyInstalled) = rustup.listComponents()
                    .find { (name, _) -> name.startsWith("clippy") } ?: return@ThrowableComputable false
                !isClippyInstalled
            },
            "Checking if Clippy is installed...",
            true,
            project
        )

        if (needInstallClippy) {
            val action = InstallComponentAction(projectDirectory, "clippy-preview")
            project.showBalloon("Clippy is not installed", NotificationType.ERROR, action)
            return
        }

        CargoCommandLine.forProject(cargoProject, "clippy").run(project, cargoProject)
    }
}
