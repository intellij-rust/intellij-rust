/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.ExecutionException
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

val RsToolchain.isRustupAvailable: Boolean get() = hasExecutable(Rustup.NAME)

fun RsToolchain.rustup(): Rustup? {
    if (!isRustupAvailable) return null
    return Rustup(this)
}

class Rustup(toolchain: RsToolchain) : RsTool(NAME, toolchain) {
    private val toolchainName: String
        get() = checkNotNull(toolchain.name) { "Non-rustup toolchain" }

    fun downloadStdlib(): DownloadResult<VirtualFile> {
        // Sometimes we have stdlib but don't have write access to install it (for example, github workflow)
        if (needInstallComponent("rust-src")) {
            val downloadProcessOutput = createBaseCommandLine(
                "component", "add", "--toolchain", toolchainName, "rust-src"
            ).execute(null)
            if (downloadProcessOutput?.isSuccess != true) {
                val message = "rustup failed: `${downloadProcessOutput?.stderr ?: ""}`"
                LOG.warn(message)
                return DownloadResult.Err(message)
            }
        }

        val sources = toolchain.rustc().getStdlibFromSysroot()
            ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
        LOG.info("stdlib path: ${sources.path}")
        fullyRefreshDirectory(sources)
        return DownloadResult.Ok(sources)
    }

    fun downloadComponent(owner: Disposable, componentName: String): DownloadResult<Unit> =
        try {
            createBaseCommandLine(
                "component", "add", "--toolchain", toolchainName, componentName,
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
            ?: return false

        return !isInstalled
    }

    fun listComponents(): List<Component> =
        createBaseCommandLine("component", "list", "--toolchain", toolchainName)
            .execute()
            ?.stdoutLines
            ?.map { Component.from(it) }
            .orEmpty()

    data class Component(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Component {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Component(name, isInstalled)
            }
        }
    }

    fun listToolchains(): List<Toolchain> =
        createBaseCommandLine("toolchain", "list", "--verbose")
            .execute()
            ?.stdoutLines
            ?.map { Toolchain.from(it) }
            .orEmpty()

    data class Toolchain(val name: String, val path: String, val isDefault: Boolean) {
        override fun toString(): String = name

        companion object {
            fun from(line: String): Toolchain {
                val before = line.substringBefore('\t')
                val name = before.removeSuffix(" (default)")
                val isDefault = before.endsWith("(default)")
                val path = line.substringAfter('\t')
                return Toolchain(name, path, isDefault)
            }
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(Rustup::class.java)

        const val NAME: String = "rustup"

        fun checkNeedInstallClippy(project: Project): Boolean = checkNeedInstallComponent(project, "clippy")

        fun checkNeedInstallRustfmt(project: Project): Boolean = checkNeedInstallComponent(project, "rustfmt")

        // We don't want to install the component if:
        // 1. It is already installed
        // 2. We don't have Rustup
        // 3. Rustup doesn't have this component
        private fun checkNeedInstallComponent(project: Project, componentName: String): Boolean {
            val rustup = project.toolchain?.rustup() ?: return false
            val needInstall = rustup.needInstallComponent(componentName)

            if (needInstall) {
                project.showBalloon(
                    "${componentName.capitalize()} is not installed",
                    NotificationType.ERROR,
                    InstallComponentAction(componentName)
                )
            }

            return needInstall
        }
    }
}
