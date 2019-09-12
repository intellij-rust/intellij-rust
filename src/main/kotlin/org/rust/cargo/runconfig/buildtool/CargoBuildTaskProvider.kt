/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskManager
import com.intellij.task.ProjectTaskNotification
import com.intellij.task.ProjectTaskResult
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.createBuildEnvironment
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

class CargoBuildTaskProvider : BeforeRunTaskProvider<CargoBuildTaskProvider.BuildTask>() {
    override fun getId(): Key<BuildTask> = ID
    override fun getName(): String = "Build"
    override fun getIcon(): Icon = AllIcons.Actions.Compile
    override fun isSingleton(): Boolean = true

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
        val buildEnvironment = createBuildEnvironment(buildConfiguration)
            ?.also { environment.copyUserDataTo(it) }
            ?: return false
        val buildableElement = CargoBuildConfiguration(buildConfiguration, buildEnvironment)

        val result = CompletableFuture<Boolean>()
        ProjectTaskManager.getInstance(configuration.project).build(arrayOf(buildableElement), object: ProjectTaskNotification {
            override fun finished(executionResult: ProjectTaskResult) {
                result.complete(executionResult.errors == 0 && !executionResult.isAborted)
            }
        })
        return result.get()
    }

    class BuildTask : BeforeRunTask<BuildTask>(ID) {
        init {
            isEnabled = true
        }
    }

    companion object {
        @JvmField
        val ID: Key<BuildTask> = Key.create("CARGO.BUILD_TASK_PROVIDER")
    }
}
