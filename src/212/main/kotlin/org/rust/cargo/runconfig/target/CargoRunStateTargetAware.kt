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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import java.nio.charset.StandardCharsets

@Suppress("UnstableApiUsage")
abstract class CargoRunStateTargetAware(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunStateBase(environment, runConfiguration, config) {

    private data class Context(val rootDirectory: String, val workingDirectory: String)

    /**
     * @param processColors if true, process ANSI escape sequences, otherwise keep escape codes in the output
     */
    override fun startProcess(processColors: Boolean): ProcessHandler {
        val request = getTargetEnvironmentRequest()
        val setup = RsCommandLineSetup(request, request.languageRuntime)
        val progressIndicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
        val cargoCommandLine = prepareCommandLine()
        val commandLine = cargo().toColoredCommandLine(project, cargoCommandLine).makeTargeted(request, setup)
        val environment = prepareRemoteEnvironment(request, setup, progressIndicator)
        val process = environment.createProcess(commandLine, progressIndicator)
        val commandRepresentation = commandLine.getCommandPresentation(environment)
        LOG.debug("Executing Cargo command: `$commandRepresentation`")
        val handler = RsProcessHandler(process, commandRepresentation, commandLine.charset, processColors)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }

    private fun GeneralCommandLine.makeTargeted(
        request: TargetEnvironmentRequest,
        setup: RsCommandLineSetup
    ): TargetedCommandLine {
        val commandLineBuilder = TargetedCommandLineBuilder(request)

        val targetExePath = setup.string(request.languageRuntime?.cargoPath ?: "cargo")
        commandLineBuilder.setExePath(targetExePath)

        val workDirectory = workDirectory
        if (workDirectory != null) {
            val targetWorkingDirectory = setup.path(workDirectory.absolutePath)
            commandLineBuilder.setWorkingDirectory(targetWorkingDirectory)
        }

        val inputFile = inputFile
        if (inputFile != null) {
            val targetInput = setup.path(inputFile.absolutePath)
            commandLineBuilder.setInputFile(targetInput)
        }

        for (parameter in parametersList.parameters) {
            val targetParameter = setup.string(parameter)
            commandLineBuilder.addParameter(targetParameter)
        }

        for ((key, value) in environment.entries) {
            if (key == "RUSTC") continue // TODO: use rustc on WSL (?)
            val targetValue = setup.string(value)
            commandLineBuilder.addEnvironmentVariable(key, targetValue)
        }

        commandLineBuilder.setCharset(StandardCharsets.UTF_8)

        return commandLineBuilder.build()
    }

    private fun getTargetEnvironmentRequest(): TargetEnvironmentRequest {
        if (!RunTargetsEnabled.get()) return LocalTargetEnvironmentRequest()
        val targetName = runConfiguration.defaultTargetName ?: return LocalTargetEnvironmentRequest()
        val config = TargetEnvironmentsManager.getInstance(environment.project).targets.findByName(targetName)
            ?: throw ExecutionException("Cannot find target $targetName")
        return config.createEnvironmentRequest(environment.project)
    }

    private fun prepareRemoteEnvironment(
        request: TargetEnvironmentRequest,
        setup: RsCommandLineSetup,
        progressIndicator: ProgressIndicator
    ): TargetEnvironment {
        val targetProgressIndicator = object : TargetProgressIndicator {
            override fun isCanceled(): Boolean = progressIndicator.isCanceled
            override fun stop() = progressIndicator.cancel()
            override fun isStopped(): Boolean = isCanceled
            override fun addText(text: String, key: Key<*>) {
                progressIndicator.text2 = text.trim()
            }
        }

        return try {
            val environment = request.prepareEnvironment(targetProgressIndicator)
            setup.provideEnvironment(environment, targetProgressIndicator)
            environment
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            throw ExecutionException("Failed to prepare remote environment: ${e.localizedMessage}", e)
        }
    }

    private val TargetEnvironmentRequest.languageRuntime: RsLanguageRuntimeConfiguration?
        get() = configuration?.runtimes?.findByType()

    companion object {
        private val LOG: Logger = logger<CargoRunStateTargetAware>()
    }
}
