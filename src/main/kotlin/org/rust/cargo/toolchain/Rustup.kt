/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.DownloadResult
import org.rust.ide.actions.InstallComponentAction
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.*
import java.nio.file.Path

private val LOG = Logger.getInstance(Rustup::class.java)

class Rustup(
    private val toolchain: RustToolchain,
    private val rustup: Path,
    private val projectDirectory: Path
) {
    fun listComponents(toolchain: String? = null, onlyInstalled: Boolean = false): List<String> =
        GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "list")
            .apply { if (onlyInstalled) addParameter("--installed") }
            .apply { if (toolchain != null) addParameters("--toolchain", toolchain) }
            .execute()
            ?.stdoutLines
            ?.map { it.substringBefore(' ') }
            .orEmpty()

    fun downloadStdlib(): DownloadResult<VirtualFile> {
        val downloadProcessOutput = GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "add", "rust-src")
            .execute(null)

        return if (downloadProcessOutput?.isSuccess == true) {
            val sources = toolchain.getStdlibFromSysroot(projectDirectory)
                ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
            fullyRefreshDirectory(sources)
            DownloadResult.Ok(sources)
        } else {
            val message = "rustup failed: `${downloadProcessOutput?.stderr ?: ""}`"
            LOG.warn(message)
            DownloadResult.Err(message)
        }
    }

    fun downloadComponent(owner: Disposable, componentName: String): DownloadResult<Unit> =
        try {
            GeneralCommandLine(rustup)
                .withWorkDirectory(projectDirectory)
                .withParameters("component", "add", componentName)
                .execute(owner, false)
            DownloadResult.Ok(Unit)
        } catch (e: ExecutionException) {
            val message = "rustup failed: `${e.message}`"
            LOG.warn(message)
            DownloadResult.Err(message)
        }

    companion object {

        fun checkNeedInstallClippy(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "clippy-preview")

        fun checkNeedInstallRustfmt(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "rustfmt-preview")

        // We don't want to install the component if:
        // 1. It is already installed
        // 2. We don't have Rustup
        // 3. Rustup doesn't have this component
        private fun checkNeedInstallComponent(
            project: Project,
            cargoProjectDirectory: Path,
            componentName: String
        ): Boolean {
            val shortName = componentName.removeSuffix("-preview")

            val needInstall = run {
                val rustup = project.toolchain?.rustup(cargoProjectDirectory) ?: return false
                val isInstalled = rustup.listComponents(onlyInstalled = true).any { it.startsWith(shortName) }
                !isInstalled
            }

            if (needInstall) {
                project.showBalloon(
                    "${shortName.capitalize()} is not installed",
                    NotificationType.ERROR,
                    InstallComponentAction(cargoProjectDirectory, componentName)
                )
            }

            return needInstall
        }
    }
}
