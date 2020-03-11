/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.filters.Filter
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.filters.RsBacktraceFilter
import org.rust.cargo.runconfig.filters.RsConsoleFilter
import org.rust.cargo.runconfig.filters.RsExplainFilter
import org.rust.cargo.runconfig.filters.RsPanicFilter
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.run
import org.rust.stdext.buildList

fun CargoCommandLine.mergeWithDefault(default: CargoCommandConfiguration): CargoCommandLine =
    copy(
        backtraceMode = default.backtrace,
        channel = default.channel,
        environmentVariables = default.env,
        allFeatures = default.allFeatures,
        nocapture = default.nocapture,
        emulateTerminal = default.emulateTerminal
    )

fun RunManager.createCargoCommandRunConfiguration(cargoCommandLine: CargoCommandLine, name: String? = null): RunnerAndConfigurationSettings {
    val runnerAndConfigurationSettings = createConfiguration(name ?: cargoCommandLine.command,
        CargoCommandConfigurationType.getInstance().factory)
    val configuration = runnerAndConfigurationSettings.configuration as CargoCommandConfiguration
    configuration.setFromCmd(cargoCommandLine)
    return runnerAndConfigurationSettings
}

val Project.hasCargoProject: Boolean get() = cargoProjects.allProjects.isNotEmpty()

fun Project.buildProject() {
    val arguments = buildList<String> {
        val settings = rustSettings
        add("--all")
        if (settings.compileAllTargets) {
            val allTargets = settings.toolchain
                ?.rawCargo()
                ?.checkSupportForBuildCheckAllTargets()
                ?: false
            if (allTargets) add("--all-targets")
        }
    }

    // Initialize run content manager
    invokeAndWaitIfNeeded {
        RunContentManager.getInstance(this)
    }

    for (cargoProject in cargoProjects.allProjects) {
        CargoCommandLine.forProject(cargoProject, "build", arguments).run(cargoProject, saveConfiguration = false)
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
        add(RsPanicFilter(cargoProject.project, dir))
        add(RsBacktraceFilter(cargoProject.project, dir, cargoProject.workspace))
    }
}

fun addFormatJsonOption(additionalArguments: MutableList<String>, formatOption: String) {
    val formatJsonOption = "$formatOption=json"
    val idx = additionalArguments.indexOf(formatOption)
    val indexArgWithValue = additionalArguments.indexOfFirst { it.startsWith(formatOption) }
    if (idx != -1) {
        if (idx < additionalArguments.size - 1) {
            if (!additionalArguments[idx + 1].startsWith("-")) {
                additionalArguments[idx + 1] = "json"
            } else {
                additionalArguments.add(idx + 1, "json")
            }
        } else {
            additionalArguments.add("json")
        }
    } else if (indexArgWithValue != -1) {
        additionalArguments[indexArgWithValue] = formatJsonOption
    } else {
        additionalArguments.add(formatJsonOption)
    }
}
