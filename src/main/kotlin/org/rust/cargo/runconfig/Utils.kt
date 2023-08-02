/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExternalizablePath
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.Filter
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jdom.Element
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.filters.*
import org.rust.cargo.runconfig.target.startProcess
import org.rust.cargo.runconfig.target.targetEnvironment
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.tools.cargo
import org.rust.openapiext.checkIsDispatchThread
import org.rust.stdext.buildList
import java.nio.file.Path
import java.nio.file.Paths

fun CargoCommandLine.mergeWithDefault(default: CargoCommandConfiguration): CargoCommandLine =
    copy(
        backtraceMode = default.backtrace,
        channel = default.channel,
        environmentVariables = default.env,
        requiredFeatures = default.requiredFeatures,
        allFeatures = default.allFeatures,
        emulateTerminal = default.emulateTerminal,
        withSudo = default.withSudo,
    )

fun RunManager.createCargoCommandRunConfiguration(cargoCommandLine: CargoCommandLine, name: String? = null): RunnerAndConfigurationSettings {
    val runnerAndConfigurationSettings = createConfiguration(
        name ?: cargoCommandLine.command,
        CargoCommandConfigurationType.getInstance().factory
    )
    val configuration = runnerAndConfigurationSettings.configuration as CargoCommandConfiguration
    configuration.setFromCmd(cargoCommandLine)
    return runnerAndConfigurationSettings
}

val Project.hasCargoProject: Boolean get() = cargoProjects.allProjects.isNotEmpty()

fun Project.buildProject() {
    checkIsDispatchThread()
    val arguments = buildList {
        val settings = rustSettings
        add("--all")
        if (settings.compileAllTargets) {
            val allTargets = settings.toolchain
                ?.cargo()
                ?.checkSupportForBuildCheckAllTargets()
                ?: false
            if (allTargets) add("--all-targets")
        }
    }

    // Initialize run content manager
    RunContentManager.getInstance(this)

    for (cargoProject in cargoProjects.allProjects) {
        CargoCommandLine.forProject(cargoProject, "build", arguments).run(cargoProject, saveConfiguration = false)
    }
}

fun Project.cleanProject() {
    checkIsDispatchThread()

    // Initialize run content manager
    RunContentManager.getInstance(this)

    for (cargoProject in cargoProjects.allProjects) {
        CargoCommandLine.forProject(cargoProject, "clean", emulateTerminal = false)
            .run(cargoProject, saveConfiguration = false)
    }
}

fun getAppropriateCargoProject(dataContext: DataContext): CargoProject? {
    val cargoProjects = dataContext.getData(CommonDataKeys.PROJECT)?.cargoProjects ?: return null
    cargoProjects.allProjects.singleOrNull()?.let { return it }

    dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        ?.let { cargoProjects.findProjectForFile(it) }
        ?.let { return it }

    return dataContext.getData(CargoToolWindow.SELECTED_CARGO_PROJECT)
        ?: cargoProjects.allProjects.firstOrNull()
}

fun createFilters(cargoProject: CargoProject?): Collection<Filter> = buildList {
    add(RsExplainFilter())
    val dir = cargoProject?.workspaceRootDir ?: cargoProject?.rootDir
    if (cargoProject != null && dir != null) {
        add(RsConsoleFilter(cargoProject.project, dir))
        add(RsDbgFilter(cargoProject.project, dir))
        add(RsPanicFilter(cargoProject.project, dir))
        add(RsBacktraceFilter(cargoProject.project, dir, cargoProject.workspace))
    }
}

fun addFormatJsonOption(additionalArguments: MutableList<String>, formatOption: String, format: String) {
    val formatJsonOption = "$formatOption=$format"
    val idx = additionalArguments.indexOf(formatOption)
    val indexArgWithValue = additionalArguments.indexOfFirst { it.startsWith(formatOption) }
    if (idx != -1) {
        if (idx < additionalArguments.size - 1) {
            if (!additionalArguments[idx + 1].startsWith("-")) {
                additionalArguments[idx + 1] = format
            } else {
                additionalArguments.add(idx + 1, format)
            }
        } else {
            additionalArguments.add(format)
        }
    } else if (indexArgWithValue != -1) {
        additionalArguments[indexArgWithValue] = formatJsonOption
    } else {
        additionalArguments.add(0, formatJsonOption)
    }
}

sealed class BuildResult {
    data class Binaries(val paths: List<String>) : BuildResult()
    sealed class ToolchainError(@Suppress("UnstableApiUsage") @DialogMessage val message: String) : BuildResult() {
        // TODO: move into bundle
        object UnsupportedMSVC : ToolchainError(RsBundle.message("dialog.message.msvc.toolchain.not.supported.please.use.gnu.toolchain"))
        object UnsupportedGNU : ToolchainError(RsBundle.message("dialog.message.gnu.toolchain.not.supported.please.use.msvc.toolchain"))
        object UnsupportedWSL : ToolchainError(RsBundle.message("dialog.message.wsl.toolchain.not.supported"))
        object MSVCWithRustGNU : ToolchainError(RsBundle.message("dialog.message.msvc.debugger.cannot.be.used.with.gnu.rust.toolchain"))
        object GNUWithRustMSVC : ToolchainError(RsBundle.message("dialog.message.gnu.debugger.cannot.be.used.with.msvc.rust.toolchain"))
        object WSLWithNonWSL : ToolchainError(
            RsBundle.message("dialog.message.html.local.debugger.cannot.be.used.with.wsl.br.use.href.https.www.jetbrains.com.help.clion.how.to.use.wsl.development.environment.in.product.html.instructions.to.configure.wsl.toolchain.html")
        )
        object NonWSLWithWSL : ToolchainError(RsBundle.message("dialog.message.wsl.debugger.cannot.be.used.with.non.wsl.rust.toolchain"))

        class Other(@DialogMessage message: String) : ToolchainError(message)
    }
}

fun Element.writeString(name: String, value: String) {
    val opt = Element("option")
    opt.setAttribute("name", name)
    opt.setAttribute("value", value)
    addContent(opt)
}

fun Element.readString(name: String): String? =
    children
        .find { it.name == "option" && it.getAttributeValue("name") == name }
        ?.getAttributeValue("value")

fun Element.writeBool(name: String, value: Boolean) {
    writeString(name, value.toString())
}

fun Element.readBool(name: String): Boolean? =
    readString(name)?.toBoolean()

fun <E : Enum<*>> Element.writeEnum(name: String, value: E) {
    writeString(name, value.name)
}

inline fun <reified E : Enum<E>> Element.readEnum(name: String): E? {
    val variantName = readString(name) ?: return null
    return try {
        java.lang.Enum.valueOf(E::class.java, variantName)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun Element.writePath(name: String, value: Path?) {
    if (value != null) {
        val s = ExternalizablePath.urlValue(value.toString())
        writeString(name, s)
    }
}

fun Element.readPath(name: String): Path? {
    return readString(name)?.let { Paths.get(ExternalizablePath.localPathValue(it)) }
}

fun CargoRunStateBase.executeCommandLine(
    commandLine: GeneralCommandLine,
    environment: ExecutionEnvironment
): DefaultExecutionResult {
    val runConfiguration = runConfiguration
    val targetEnvironment = runConfiguration.targetEnvironment
    val context = ConfigurationExtensionContext()

    val extensionManager = RsRunConfigurationExtensionManager.getInstance()
    extensionManager.patchCommandLine(runConfiguration, environment, commandLine, context)
    extensionManager.patchCommandLineState(runConfiguration, environment, this, context)
    val handler = commandLine.startProcess(environment.project, targetEnvironment, processColors = true, uploadExecutable = true)
    extensionManager.attachExtensionsToProcess(runConfiguration, handler, environment, context)

    val console = consoleBuilder.console
    console.attachToProcess(handler)
    return DefaultExecutionResult(console, handler)
}
