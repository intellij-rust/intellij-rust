/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.RsBundle
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.wasmpack.WasmPackBuildTaskProvider.Companion.WASM_TARGET
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.util.DownloadResult
import org.rust.ide.actions.InstallComponentAction
import org.rust.ide.actions.InstallTargetAction
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.*
import org.rust.stdext.RsResult
import org.rust.stdext.capitalized
import org.rust.stdext.unwrapOrThrow
import java.nio.file.Path

private val LOG: Logger = logger<Rustup>()

val RsToolchainBase.isRustupAvailable: Boolean get() = hasExecutable(Rustup.NAME)

fun RsToolchainBase.rustup(cargoProjectDirectory: Path): Rustup? {
    if (!isRustupAvailable) return null
    return Rustup(this, cargoProjectDirectory)
}

class Rustup(toolchain: RsToolchainBase, private val projectDirectory: Path) : RsTool(NAME, toolchain) {

    data class Component(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Component {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Component(name, isInstalled)
            }
        }
    }

    data class Target(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Target {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Target(name, isInstalled)
            }
        }
    }

    private fun listComponents(): List<Component> =
        createBaseCommandLine(
            "component", "list",
            workingDirectory = projectDirectory
        ).execute(toolchain.executionTimeoutInMilliseconds)?.stdoutLines?.map { Component.from(it) }.orEmpty()

    private fun listTargets(): List<Target> =
        createBaseCommandLine(
            "target", "list",
            workingDirectory = projectDirectory
        ).execute(toolchain.executionTimeoutInMilliseconds)?.stdoutLines?.map { Target.from(it) }.orEmpty()

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
                commandLine.execute(owner, listener = listener).ignoreExitCode().unwrapOrThrow()
            }

            if (downloadProcessOutput?.isSuccess != true) {
                val message = RsBundle.message("notification.content.rustup.failed2", downloadProcessOutput?.stderr ?: "")
                LOG.warn(message)
                return DownloadResult.Err(message)
            }
        }

        val sources = toolchain.rustc().getStdlibFromSysroot(projectDirectory)
            ?: return DownloadResult.Err(RsBundle.message("notification.content.failed.to.find.stdlib.in.sysroot"))
        LOG.info("stdlib path: ${sources.path}")
        fullyRefreshDirectory(sources)
        return DownloadResult.Ok(sources)
    }

    fun downloadComponent(owner: Disposable, componentName: String): DownloadResult<Unit> =
        createBaseCommandLine(
            "component", "add", componentName,
            workingDirectory = projectDirectory
        ).execute(owner).convertResult()

    fun downloadTarget(owner: Disposable, targetName: String): DownloadResult<Unit> =
        createBaseCommandLine(
            "target", "add", targetName,
            workingDirectory = projectDirectory
        ).execute(owner).convertResult()

    fun activeToolchainName(): String? {
        val output = createBaseCommandLine("show", "active-toolchain", workingDirectory = projectDirectory)
            .execute(toolchain.executionTimeoutInMilliseconds) ?: return null
        if (!output.isSuccess) return null
        // Expected outputs:
        //  1.48.0-x86_64-apple-darwin (default)
        //  stable-x86_64-apple-darwin (overridden by '/path/to/rust-toolchain.toml')
        //  nightly-x86_64-apple-darwin (directory override for '/path/to/override/dir')
        return output.stdout.substringBefore("(").trim()
    }

    private fun RsProcessResult<ProcessOutput>.convertResult() =
        when (this) {
            is RsResult.Ok -> DownloadResult.Ok(Unit)
            is RsResult.Err -> {
                val message = RsBundle.message("notification.content.rustup.failed", err.message?:"")
                LOG.warn(message)
                DownloadResult.Err(message)
            }
        }

    private fun needInstallComponent(componentName: String): Boolean {
        val isInstalled = listComponents()
            .find { (name, _) -> name.startsWith(componentName) }
            ?.isInstalled
            ?: return false

        return !isInstalled
    }

    private fun needInstallTarget(targetName: String): Boolean {
        val isInstalled = listTargets()
            .find { it.name == targetName }
            ?.isInstalled
            ?: return false

        return !isInstalled
    }

    companion object {
        const val NAME: String = "rustup"

        fun checkNeedInstallClippy(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "clippy")

        fun checkNeedInstallRustfmt(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "rustfmt")

        fun checkNeedInstallLlvmTools(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallComponent(project, cargoProjectDirectory, "llvm-tools-preview", "llvm-tools")

        fun checkNeedInstallWasmTarget(project: Project, cargoProjectDirectory: Path): Boolean =
            checkNeedInstallTarget(project, cargoProjectDirectory, WASM_TARGET)

        // We don't want to install the component if:
        // 1. It is already installed
        // 2. We don't have Rustup
        // 3. Rustup doesn't have this component
        private fun checkNeedInstallComponent(
            project: Project,
            cargoProjectDirectory: Path,
            componentName: String,
            componentPresentableName: String = componentName.capitalized()
        ): Boolean {
            val rustup = project.toolchain?.rustup(cargoProjectDirectory) ?: return false
            val needInstall = rustup.needInstallComponent(componentName)

            if (needInstall) {
                project.showBalloon(
                    RsBundle.message("notification.content.not.installed", componentPresentableName),
                    NotificationType.ERROR,
                    InstallComponentAction(cargoProjectDirectory, componentName)
                )
            }

            return needInstall
        }

        fun checkNeedInstallTarget(
            project: Project,
            cargoProjectDirectory: Path,
            targetName: String
        ): Boolean {
            val rustup = project.toolchain?.rustup(cargoProjectDirectory) ?: return false
            val needInstall = rustup.needInstallTarget(targetName)

            if (needInstall) {
                project.showBalloon(
                    RsBundle.message("notification.content.target.not.installed", targetName),
                    NotificationType.ERROR,
                    InstallTargetAction(cargoProjectDirectory, targetName)
                )
            }

            return needInstall
        }
    }
}
