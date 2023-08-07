/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.nullize
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RustChannel
import org.rust.ide.actions.RustfmtEditSettingsAction
import org.rust.ide.notifications.showBalloon
import org.rust.lang.core.psi.ext.edition
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.*
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.unwrapOrElse
import java.nio.file.Path

fun RsToolchainBase.rustfmt(): Rustfmt = Rustfmt(this)

class Rustfmt(toolchain: RsToolchainBase) : RustupComponent(NAME, toolchain) {

    fun reformatDocumentTextOrNull(cargoProject: CargoProject, document: Document): String? {
        val project = cargoProject.project
        val stdout = reformatTextDocument(cargoProject, document, project)
            ?.unwrapOrElse { e ->
                e.showRustfmtError(project)
                if (isUnitTestMode) throw e else return null
            }?.stdout
        return stdout.nullize()
    }

    fun reformatTextDocument(cargoProject: CargoProject, document: Document, project: Project): RsProcessResult<ProcessOutput>? =
        createCommandLine(cargoProject, document)
            ?.execute(project, stdIn = document.text.toByteArray())

    fun createCommandLine(cargoProject: CargoProject, document: Document): GeneralCommandLine? {
        val file = document.virtualFile ?: return null
        if (file.isNotRustFile || !file.isValid) return null

        val project = cargoProject.project
        val settings = project.rustfmtSettings

        val arguments = ParametersListUtil.parse(settings.additionalArguments)
        val cleanArguments = mutableListOf<String>()

        val toolchain = arguments.firstOrNull()?.takeIf { it.startsWith("+") }
        when {
            settings.channel != RustChannel.DEFAULT -> cleanArguments += "+${settings.channel}"
            toolchain != null -> cleanArguments += toolchain
        }

        var idx = if (toolchain == null) 0 else 1
        while (idx < arguments.size) {
            val arg = arguments[idx]
            when {
                arg == "--emit" -> idx += 2
                arg.startsWith("--emit") -> idx += 1
                else -> {
                    cleanArguments += arg
                    idx += 1
                }
            }
        }
        cleanArguments.add("--emit=stdout")

        cleanArguments.addArgument("config-path") {
            findConfigPathRecursively(file.parent, stopAt = cargoProject.workingDirectory)?.toString()
        }

        cleanArguments.addArgument("edition") {
            if (cargoProject.rustcInfo?.version == null) return@addArgument null
            val edition = runReadAction {
                val psiFile = file.toPsiFile(project)
                psiFile?.edition ?: Edition.DEFAULT
            }
            edition.presentation
        }

        return createBaseCommandLine(cleanArguments, cargoProject.workingDirectory, settings.envs)
    }

    fun reformatCargoProject(
        cargoProject: CargoProject,
        owner: Disposable = cargoProject.project
    ): RsProcessResult<Unit> {
        val project = cargoProject.project
        val settings = project.rustfmtSettings
        val arguments = ParametersListUtil.parse(settings.additionalArguments).toMutableList()
        val toolchain = if (arguments.firstOrNull()?.startsWith("+") == true) {
            arguments.removeFirst()
        } else {
            null
        }
        val commandLine = CargoCommandLine.forProject(
            cargoProject,
            "fmt",
            listOf("--all", "--") + arguments,
            false,
            toolchain,
            settings.channel,
            EnvironmentVariablesData.create(settings.envs, true)
        )

        return project.computeWithCancelableProgress(RsBundle.message("progress.title.reformatting.cargo.project.with.rustfmt")) {
            project.toolchain
                ?.cargoOrWrapper(cargoProject.workingDirectory)
                ?.toGeneralCommandLine(project, commandLine)
                ?.execute(owner)
                ?.map { }
                ?.mapErr { e ->
                    e.showRustfmtError(project)
                    e
                }
                ?: Ok(Unit)
        }
    }

    companion object {
        const val NAME: String = "rustfmt"

        private val CONFIG_FILES: List<String> = listOf("rustfmt.toml", ".rustfmt.toml")

        private fun MutableList<String>.addArgument(flagName: String, value: () -> String?) {
            if (any { it.startsWith("--$flagName") }) return
            val flagValue = value() ?: return
            add("--$flagName=$flagValue")
        }

        private fun findConfigPathRecursively(directory: VirtualFile, stopAt: Path): Path? {
            val path = directory.pathAsPath
            if (!path.startsWith(stopAt) || path == stopAt) return null
            if (directory.children.any { it.name in CONFIG_FILES }) return path
            return findConfigPathRecursively(directory.parent, stopAt)
        }

        private fun RsProcessExecutionException.showRustfmtError(project: Project) {
            val message = message.orEmpty().trimEnd('\n')
            if (message.isNotEmpty()) {
                val html = "<html>${message.escaped.replace("\n", "<br>")}</html>"
                project.showBalloon(RsBundle.message("notification.title.rustfmt"), html, NotificationType.ERROR, RustfmtEditSettingsAction(RsBundle.message("action.show.settings.text")))
            }
        }
    }
}
