/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.wasmpack.createWasmPackCommandRunConfiguration
import java.nio.file.Path

data class WasmPackCommandLine(
    val command: String,
    val workingDirectory: Path,
    val additionalArguments: List<String> = emptyList()
)

fun WasmPackCommandLine.run(
    cargoProject: CargoProject,
    presentableName: String = command,
    saveConfiguration: Boolean = true
) {
    val name = cargoProject.localConfigurationName(presentableName)
    val configuration = createWasmPackCommandConfiguration(
        cargoProject.project, this, name, saveConfiguration
    )
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    ProgramRunnerUtil.executeConfiguration(configuration, executor)
}

private fun createWasmPackCommandConfiguration(
    project: Project,
    wasmPackCommandLine: WasmPackCommandLine,
    name: String? = null,
    saveConfiguration: Boolean
): RunnerAndConfigurationSettings {
    val runManager = RunManagerEx.getInstanceEx(project)
    return runManager.createWasmPackCommandRunConfiguration(wasmPackCommandLine, name).apply {
        if (saveConfiguration) {
            runManager.setTemporaryConfiguration(this)
        }
    }
}
