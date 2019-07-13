/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
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
        if (settings.useOffline) add("-Zoffline")
    }

    // Initialize run content manager
    ApplicationManager.getApplication().invokeAndWait {
        ExecutionManagerImpl.getInstance(this).contentManager
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
