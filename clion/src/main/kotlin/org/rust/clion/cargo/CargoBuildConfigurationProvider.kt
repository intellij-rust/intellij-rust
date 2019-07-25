/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.build.CLionBuildConfigurationProvider
import org.rust.cargo.runconfig.CargoCommandRunner
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class CargoBuildConfigurationProvider : CLionBuildConfigurationProvider {
    override fun getBuildableConfigurations(project: Project): List<CLionCargoBuildConfiguration> {
        val runManager = RunManager.getInstance(project) as? RunManagerImpl ?: return emptyList()
        val configuration = runManager.selectedConfiguration?.configuration as? CargoCommandConfiguration
            ?: return emptyList()
        val buildConfiguration = getBuildConfiguration(configuration) ?: return emptyList()
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        val runner = ProgramRunner.findRunnerById(CargoCommandRunner.RUNNER_ID) ?: return emptyList()
        val settings = RunnerAndConfigurationSettingsImpl(runManager, buildConfiguration)
        val environment = ExecutionEnvironment(executor, runner, settings, project)
        return listOf(CLionCargoBuildConfiguration(buildConfiguration, environment))
    }
}
