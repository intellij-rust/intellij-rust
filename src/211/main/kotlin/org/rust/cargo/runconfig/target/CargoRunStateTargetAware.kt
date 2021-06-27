/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import java.nio.charset.StandardCharsets

class CargoRunStateTargetAware(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
): CargoRunStateBase(environment, runConfiguration, config) {
    /**
     * @param processColors if true, process ANSI escape sequences, otherwise keep escape codes in the output
     */
    override fun startProcess(processColors: Boolean): ProcessHandler {
        val commandLine = cargo().toColoredCommandLine(environment.project, prepareCommandLine())
        LOG.debug("Executing Cargo command: `${commandLine.commandLineString}`")
        val handler = RsProcessHandler(commandLine, processColors)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
