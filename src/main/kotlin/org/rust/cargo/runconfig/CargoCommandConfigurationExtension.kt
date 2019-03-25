/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configuration.RunConfigurationExtensionBase
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.UserDataHolderBase
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class ConfigurationExtensionContext : UserDataHolderBase()

abstract class CargoCommandConfigurationExtension : RunConfigurationExtensionBase<CargoCommandConfiguration>() {
    private val LOG: Logger = Logger.getInstance(CargoCommandConfigurationExtension::class.java)

    abstract fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    )

    abstract fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    )

    open fun patchCommandLineState(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        state: CommandLineState,
        context: ConfigurationExtensionContext
    ) {
    }

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String
    ) {
        LOG.error("use the other overload of 'patchCommandLine' method")
    }

    companion object {
        val EP_NAME = ExtensionPointName.create<CargoCommandConfigurationExtension>("org.rust.runConfigurationExtension")
    }
}
