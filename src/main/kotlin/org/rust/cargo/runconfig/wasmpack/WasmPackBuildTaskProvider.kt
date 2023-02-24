/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.runconfig.buildtool.RsBuildTaskProvider
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.util.splitOnDoubleDash
import org.rust.openapiext.project
import org.rust.stdext.buildList

class WasmPackBuildTaskProvider : RsBuildTaskProvider<WasmPackBuildTaskProvider.BuildTask>() {
    override fun getId(): Key<BuildTask> = ID

    override fun createTask(runConfiguration: RunConfiguration): BuildTask? =
        if (runConfiguration is WasmPackCommandConfiguration) BuildTask() else null

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: BuildTask
    ): Boolean {
        if (configuration !is WasmPackCommandConfiguration) return false

        val project = context.project ?: return false
        val cargoProjectDirectory = configuration.workingDirectory ?: return false
        if (Rustup.checkNeedInstallWasmTarget(project, cargoProjectDirectory)) return false

        val configurationArgs = ParametersListUtil.parse(configuration.command)
        val (preArgs, postArgs) = splitOnDoubleDash(configurationArgs)
        val configurationCommand = configurationArgs.firstOrNull() ?: return false

        val parameters = buildList {
            add("build")
            addAll(postArgs)

            if (configurationCommand == "test") {
                add("--tests")
            }

            if ("--dev" !in preArgs) {
                add("--release")
            }

            if ("--target" !in postArgs) {
                add("--target")
                add(WASM_TARGET)
            }
        }
        val buildCommand = ParametersListUtil.join(parameters)

        val buildConfiguration = CargoCommandConfiguration(
            project, configuration.name, CargoCommandConfigurationType.getInstance().factory
        ).apply {
            command = buildCommand
            workingDirectory = configuration.workingDirectory
            emulateTerminal = false
        }

        return doExecuteTask(buildConfiguration, environment)
    }

    class BuildTask : RsBuildTaskProvider.BuildTask<BuildTask>(ID)

    companion object {
        val ID: Key<BuildTask> = Key.create("WASM_PACK.BUILD_TASK_PROVIDER")

        const val WASM_TARGET: String = "wasm32-unknown-unknown"
    }
}
