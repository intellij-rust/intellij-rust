/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class RsRunConfigurationExtensionManager : RunConfigurationExtensionsManager<CargoCommandConfiguration, CargoCommandConfigurationExtension>(CargoCommandConfigurationExtension.EP_NAME) {
    fun attachExtensionsToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        processEnabledExtensions(configuration, environment.runnerSettings) {
            it.attachToProcess(configuration, handler, environment, context)
        }
    }

    fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        processEnabledExtensions(configuration, environment.runnerSettings) {
            it.patchCommandLine(configuration, environment, cmdLine, context)
        }
    }

    fun patchCommandLineState(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        state: CommandLineState,
        context: ConfigurationExtensionContext
    ) {
        processEnabledExtensions(configuration, environment.runnerSettings) {
            it.patchCommandLineState(configuration, environment, state, context)
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): RsRunConfigurationExtensionManager = service()
    }
}
