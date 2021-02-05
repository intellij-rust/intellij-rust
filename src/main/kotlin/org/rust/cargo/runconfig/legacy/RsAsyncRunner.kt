/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.legacy

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.runconfig.*
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.tools.Cargo.Companion.getCargoCommonPatch
import org.rust.cargo.toolchain.tools.cargo
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs
import org.rust.openapiext.JsonUtils.tryParseJsonObject
import org.rust.openapiext.saveAllDocuments
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This runner is used if [isBuildToolWindowEnabled] is false.
 */
abstract class RsAsyncRunner(
    private val executorId: String,
    private val errorMessageTitle: String
) : AsyncProgramRunner<RunnerSettings>() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != this.executorId || profile !is CargoCommandConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return !profile.project.isBuildToolWindowEnabled &&
            !isBuildConfiguration(profile) &&
            getBuildConfiguration(profile) != null
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        saveAllDocuments()

        state as CargoRunStateBase

        val commandLine = state.prepareCommandLine(getCargoCommonPatch(environment.project))
        val (commandArguments, executableArguments) = parseArgs(commandLine.command, commandLine.additionalArguments)

        val isTestRun = commandLine.command == "test"
        val cmdHasNoRun = "--no-run" in commandLine.additionalArguments
        val buildCommand = if (isTestRun) {
            if (cmdHasNoRun) commandLine else commandLine.prependArgument("--no-run")
        } else {
            commandLine.copy(command = "build", additionalArguments = commandArguments)
        }.copy(emulateTerminal = false)

        val getRunCommand = { executablePath: Path ->
            with(commandLine) {
                state.toolchain.createGeneralCommandLine(
                    executablePath,
                    workingDirectory,
                    redirectInputFrom,
                    backtraceMode,
                    environmentVariables,
                    executableArguments,
                    emulateTerminal,
                    patchToRemote = false
                )
            }
        }

        return buildProjectAndGetBinaryArtifactPath(environment.project, buildCommand, state, isTestRun)
            .then { binary ->
                if (isTestRun && cmdHasNoRun) return@then null
                val path = binary?.path ?: return@then null
                val runCommand = getRunCommand(path)
                getRunContentDescriptor(state, environment, runCommand)
            }
    }

    open fun getRunContentDescriptor(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runCommand: GeneralCommandLine
    ): RunContentDescriptor? = showRunContent(executeCommandLine(state, runCommand, environment), environment)

    private fun executeCommandLine(
        state: CargoRunStateBase,
        cmd: GeneralCommandLine,
        environment: ExecutionEnvironment
    ): DefaultExecutionResult {
        val runConfiguration = state.runConfiguration
        val context = ConfigurationExtensionContext()

        val manager = RsRunConfigurationExtensionManager.getInstance()
        manager.patchCommandLine(runConfiguration, environment, cmd, context)
        manager.patchCommandLineState(runConfiguration, environment, state, context)

        val handler = state.toolchain.startProcess(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination

        manager.attachExtensionsToProcess(runConfiguration, handler, environment, context)

        val console = state.consoleBuilder.console
        console.attachToProcess(handler)
        return DefaultExecutionResult(console, handler)
    }

    open fun checkToolchainConfigured(project: Project): Boolean = true

    open fun checkToolchainSupported(host: String): BuildResult.ToolchainError? = null

    open fun processUnsupportedToolchain(
        project: Project,
        toolchainError: BuildResult.ToolchainError,
        promise: AsyncPromise<Binary?>
    ) {
        project.showErrorDialog(toolchainError.message)
        promise.setResult(null)
    }

    private fun buildProjectAndGetBinaryArtifactPath(
        project: Project,
        command: CargoCommandLine,
        state: CargoRunStateBase,
        isTestBuild: Boolean
    ): Promise<Binary?> {
        val promise = AsyncPromise<Binary?>()
        val toolchain = state.toolchain
        val cargo = toolchain.cargo()

        val processForUserOutput = ProcessOutput()
        val processForUser = toolchain.startProcess(cargo.toColoredCommandLine(project, command))

        processForUser.addProcessListener(CapturingProcessAdapter(processForUserOutput))

        invokeLater {
            if (!checkToolchainConfigured(project)) {
                promise.setResult(null)
                return@invokeLater
            }

            RunContentExecutor(project, processForUser)
                .apply { createFilters(state.cargoProject).forEach { withFilter(it) } }
                .withAfterCompletion {
                    if (processForUserOutput.exitCode != 0) {
                        promise.setResult(null)
                        return@withAfterCompletion
                    }

                    object : Task.Backgroundable(project, "Building Cargo project") {
                        var result: BuildResult? = null

                        override fun run(indicator: ProgressIndicator) {
                            indicator.isIndeterminate = true
                            val host = state.rustVersion()?.host.orEmpty()
                            result = checkToolchainSupported(host)
                            if (result != null) return

                            val processForJson = CapturingProcessHandler(
                                cargo.toGeneralCommandLine(project, command.prependArgument("--message-format=json"))
                            )
                            val output = processForJson.runProcessWithProgressIndicator(indicator)
                            if (output.isCancelled || output.exitCode != 0) {
                                promise.setResult(null)
                                return
                            }

                            result = output.stdoutLines.asSequence()
                                .mapNotNull { tryParseJsonObject(it) }
                                .mapNotNull { CargoMetadata.Artifact.fromJson(it) }
                                .filter { (_, target, profile) ->
                                    val isSuitableTarget = when (target.cleanKind) {
                                        CargoMetadata.TargetKind.BIN -> true
                                        CargoMetadata.TargetKind.EXAMPLE -> {
                                            // TODO: support cases when crate types list contains not only binary
                                            target.cleanCrateTypes.singleOrNull() == CargoMetadata.CrateType.BIN
                                        }
                                        CargoMetadata.TargetKind.TEST -> true
                                        CargoMetadata.TargetKind.LIB -> profile.test
                                        else -> false
                                    }
                                    isSuitableTarget && (!isTestBuild || profile.test)
                                }
                                .flatMap { it.executables.asSequence() }
                                .let { BuildResult.Binaries(it.toList()) }
                        }

                        override fun onSuccess() {
                            when (val result = result!!) {
                                is BuildResult.ToolchainError -> {
                                    processUnsupportedToolchain(project, result, promise)
                                }
                                is BuildResult.Binaries -> {
                                    val binaries = result.paths
                                    when {
                                        binaries.isEmpty() -> {
                                            project.showErrorDialog("Can't find a binary")
                                            promise.setResult(null)
                                        }
                                        binaries.size > 1 -> {
                                            project.showErrorDialog("More than one binary was produced. " +
                                                "Please specify `--bin`, `--lib`, `--test` or `--example` flag explicitly.")
                                            promise.setResult(null)
                                        }
                                        else -> promise.setResult(Binary(Paths.get(binaries.single())))
                                    }
                                }
                            }
                        }

                        override fun onThrowable(error: Throwable) = promise.setResult(null)
                    }.queue()
                }.run()
        }

        return promise
    }

    protected fun Project.showErrorDialog(message: String) {
        Messages.showErrorDialog(this, message, errorMessageTitle)
    }

    companion object {
        class Binary(val path: Path)
    }
}
