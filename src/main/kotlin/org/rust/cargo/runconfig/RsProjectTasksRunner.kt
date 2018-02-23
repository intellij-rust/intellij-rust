/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.task.*
import org.rust.cargo.util.cargoProjectRoot

class RsProjectTasksRunner : ProjectTaskRunner() {
    override fun run(project: Project, context: ProjectTaskContext, callback: ProjectTaskNotification?, tasks: MutableCollection<out ProjectTask>) {
        project.buildProject()
    }

    override fun canRun(projectTask: ProjectTask): Boolean =
        projectTask is ModuleBuildTask && projectTask.module.cargoProjectRoot != null

    override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? =
        null
}
