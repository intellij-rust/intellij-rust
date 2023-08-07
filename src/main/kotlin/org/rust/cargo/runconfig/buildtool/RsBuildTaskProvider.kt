/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskManager
import org.rust.RsBundle
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.createBuildEnvironment
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

abstract class RsBuildTaskProvider<T : RsBuildTaskProvider.BuildTask<T>> : BeforeRunTaskProvider<T>() {
    override fun getName(): String = RsBundle.message("build")
    override fun getIcon(): Icon = AllIcons.Actions.Compile
    override fun isSingleton(): Boolean = true

    protected fun doExecuteTask(buildConfiguration: CargoCommandConfiguration, environment: ExecutionEnvironment): Boolean {
        val buildEnvironment = createBuildEnvironment(buildConfiguration, environment) ?: return false
        val buildableElement = CargoBuildConfiguration(buildConfiguration, buildEnvironment)

        val result = CompletableFuture<Boolean>()
        ProjectTaskManager.getInstance(environment.project).build(buildableElement).onProcessed {
            result.complete(!it.hasErrors() && !it.isAborted)
        }
        return result.get()
    }

    abstract class BuildTask<T : BuildTask<T>>(providerId: Key<T>) : BeforeRunTask<T>(providerId) {
        init {
            isEnabled = true
        }
    }
}
