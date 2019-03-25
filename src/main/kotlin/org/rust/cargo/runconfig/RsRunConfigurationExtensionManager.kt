/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class RsRunConfigurationExtensionManager : RunConfigurationExtensionsManager<CargoCommandConfiguration, CargoCommandConfigurationExtension>(CargoCommandConfigurationExtension.EP_NAME) {
    fun attachExtensionsToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        runnerSettings: RunnerSettings?,
        runnerId: String,
        context: ConfigurationExtensionContext
    ) {
        processEnabledExtensions(configuration, runnerSettings) {
            it.attachToProcess(configuration, handler, environment, runnerSettings, runnerId, context)
        }
    }

    fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String,
        context: ConfigurationExtensionContext
    ) {
        processEnabledExtensions(configuration, runnerSettings) {
            it.patchCommandLine(configuration, runnerSettings, cmdLine, runnerId, context)
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): RsRunConfigurationExtensionManager = service()
    }
}
