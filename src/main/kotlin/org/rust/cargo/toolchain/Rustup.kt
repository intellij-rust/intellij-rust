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
    data class Component(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Component {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Component(name, isInstalled)
            }
        }
    }

    fun listComponents(): List<Component> =
        GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "list")
            .execute()
            ?.stdoutLines
            ?.map { Component.from(it) }
            ?: emptyList()

    fun downloadStdlib(): DownloadResult<VirtualFile> {
        // Sometimes we have stdlib but don't have write access to install it (for example, github workflow)
        if (needInstallComponent("rust-src")) {
            val downloadProcessOutput = GeneralCommandLine(rustup)
                .withWorkDirectory(projectDirectory)
                .withParameters("component", "add", "rust-src")
                .execute(null)
            if (downloadProcessOutput?.isSuccess != true) {
                val message = "rustup failed: `${downloadProcessOutput?.stderr ?: ""}`"
                LOG.warn(message)
                return DownloadResult.Err(message)
            }
        }

        val sources = toolchain.getStdlibFromSysroot(projectDirectory)
            ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
        LOG.info("stdlib path: ${sources.path}")
        fullyRefreshDirectory(sources)
        return DownloadResult.Ok(sources)
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

    private fun needInstallComponent(componentName: String): Boolean {
        val isInstalled = listComponents()
            .find { (name, _) -> name.startsWith(componentName) }
            ?.isInstalled
            ?: return false

        return !isInstalled
    }

    companion object {

        fun checkNeedInstallClippy(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "clippy")

        fun checkNeedInstallRustfmt(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "rustfmt")

        // We don't want to install the component if:
        // 1. It is already installed
        // 2. We don't have Rustup
        // 3. Rustup doesn't have this component
        private fun checkNeedInstallComponent(
            project: Project,
            cargoProjectDirectory: Path,
            componentName: String
        ): Boolean {
            val rustup = project.toolchain?.rustup(cargoProjectDirectory) ?: return false
            val needInstall = rustup.needInstallComponent(componentName)

            if (needInstall) {
                project.showBalloon(
                    "${componentName.capitalize()} is not installed",
                    NotificationType.ERROR,
                    InstallComponentAction(cargoProjectDirectory, componentName)
                )
            }

            return needInstall
        }
    }
}
