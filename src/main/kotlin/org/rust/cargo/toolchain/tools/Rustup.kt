/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.util.DownloadResult
import org.rust.ide.actions.InstallComponentAction
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.execute
import org.rust.openapiext.fullyRefreshDirectory
import org.rust.openapiext.isSuccess
import java.nio.file.Path

private val LOG = Logger.getInstance(Rustup::class.java)

val RsToolchain.isRustupAvailable: Boolean get() = hasExecutable(Rustup.NAME)

fun RsToolchain.rustup(cargoProjectDirectory: Path): Rustup? {
    if (!isRustupAvailable) return null
    return Rustup(this, cargoProjectDirectory)
}

class Rustup(toolchain: RsToolchain, private val projectDirectory: Path) : RsTool(NAME, toolchain) {

    data class Component(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Component {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Component(name, isInstalled)
            }
        }
    }

    private fun listComponents(): List<Component> =
        createBaseCommandLine(
            "component", "list", "--installed",
            workingDirectory = projectDirectory
        ).execute()?.stdoutLines?.map { Component(it.trim(), true) }.orEmpty()

    fun downloadStdlib(owner: Disposable? = null, listener: ProcessListener? = null): DownloadResult<VirtualFile> {
        // Sometimes we have stdlib but don't have write access to install it (for example, github workflow)
        if (needInstallComponent("rust-src")) {
            val commandLine = createBaseCommandLine(
                "component", "add", "rust-src",
                workingDirectory = projectDirectory
            )

            val downloadProcessOutput = if (owner == null) {
                commandLine.execute(null)
            } else {
                commandLine.execute(owner, listener = listener)
            }

            if (downloadProcessOutput?.isSuccess != true) {
                val message = "rustup failed: `${downloadProcessOutput?.stderr ?: ""}`"
                LOG.warn(message)
                return DownloadResult.Err(message)
            }
        }

        val sources = toolchain.rustc().getStdlibFromSysroot(projectDirectory)
            ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
        LOG.info("stdlib path: ${sources.path}")
        fullyRefreshDirectory(sources)
        return DownloadResult.Ok(sources)
    }

    fun downloadComponent(owner: Disposable, componentName: String): DownloadResult<Unit> =
        try {
            createBaseCommandLine(
                "component", "add", componentName,
                workingDirectory = projectDirectory
            ).execute(owner, false)
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
            ?: return true

        return !isInstalled
    }

    companion object {
        const val NAME: String = "rustup"

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
