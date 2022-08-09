/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallTarget

class CargoBuildTaskProvider : RsBuildTaskProvider<CargoBuildTaskProvider.BuildTask>() {
    override fun getId(): Key<BuildTask> = ID

    override fun createTask(runConfiguration: RunConfiguration): BuildTask? =
        if (runConfiguration is CargoCommandConfiguration) BuildTask() else null

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: BuildTask
    ): Boolean {
        if (configuration !is CargoCommandConfiguration) return false
        val buildConfiguration = getBuildConfiguration(configuration) ?: return true

        val projectDirectory = buildConfiguration.workingDirectory ?: return false

        val configArgs = ParametersListUtil.parse(buildConfiguration.command)
        val targetFlagIdx = configArgs.indexOfFirst { it.startsWith("--target") }
        val targetFlag = if (targetFlagIdx != -1) configArgs[targetFlagIdx] else null
        val targetTriple = when {
            targetFlag == "--target" -> configArgs.getOrNull(targetFlagIdx + 1)
            targetFlag?.startsWith("--target=") == true -> unquoteString(targetFlag.substringAfter("="))
            else -> null
        }

        if (targetTriple != null && checkNeedInstallTarget(configuration.project, projectDirectory, targetTriple)) {
            return false
        }

        return doExecuteTask(buildConfiguration, environment)
    }

    class BuildTask : RsBuildTaskProvider.BuildTask<BuildTask>(ID)

    companion object {
        @JvmField
        val ID: Key<BuildTask> = Key.create("CARGO.BUILD_TASK_PROVIDER")
    }
}
