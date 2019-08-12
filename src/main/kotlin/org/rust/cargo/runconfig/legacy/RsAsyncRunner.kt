/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.legacy

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
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
import com.intellij.openapi.application.ApplicationManager
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
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.Cargo.Companion.cargoCommonPatch
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.prependArgument
import org.rust.cargo.util.CargoArgsParser.Companion.parseArgs
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

        (state as CargoRunStateBase).addCommandLinePatch(cargoCommonPatch)
        val commandLine = state.prepareCommandLine()
        val (commandArguments, executableArguments) = parseArgs(commandLine.command, commandLine.additionalArguments)

        val isTestRun = commandLine.command == "test"
        val cmdHasNoRun = "--no-run" in commandLine.additionalArguments
        val buildCommand = if (isTestRun) {
            if (cmdHasNoRun) commandLine else commandLine.prependArgument("--no-run")
        } else {
            commandLine.copy(command = "build", additionalArguments = commandArguments)
        }

        val getRunCommand = { executablePath: Path ->
            with(buildCommand) {
                Cargo.createGeneralCommandLine(
                    executablePath,
                    workingDirectory,
                    backtraceMode,
                    environmentVariables,
                    executableArguments,
                    emulateTerminal
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

        val handler = RsKillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination

        manager.attachExtensionsToProcess(runConfiguration, handler, environment, context)

        val console = state.consoleBuilder.console
        console.attachToProcess(handler)
        return DefaultExecutionResult(console, handler)
    }

    open fun checkToolchainConfigured(project: Project): Boolean = true

    open fun checkToolchainSupported(state: CargoRunStateBase): Boolean = true

    open fun processUnsupportedToolchain(project: Project, promise: AsyncPromise<Binary?>) {}

    private fun buildProjectAndGetBinaryArtifactPath(
        project: Project,
        command: CargoCommandLine,
        state: CargoRunStateBase,
        isTestBuild: Boolean
    ): Promise<Binary?> {
        val promise = AsyncPromise<Binary?>()
        val cargo = state.cargo()

        val processForUserOutput = ProcessOutput()
        val processForUser = RsKillableColoredProcessHandler(cargo.toColoredCommandLine(command))

        processForUser.addProcessListener(CapturingProcessAdapter(processForUserOutput))

        ApplicationManager.getApplication().invokeLater {
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
                            if (!checkToolchainSupported(state)) {
                                result = BuildResult.UnsupportedToolchain
                                return
                            }

                            val processForJson = CapturingProcessHandler(
                                cargo.toGeneralCommandLine(command.prependArgument("--message-format=json"))
                            )
                            val output = processForJson.runProcessWithProgressIndicator(indicator)
                            if (output.isCancelled || output.exitCode != 0) {
                                promise.setResult(null)
                                return
                            }

                            result = output.stdoutLines
                                .mapNotNull {
                                    try {
                                        val jsonElement = PARSER.parse(it)
                                        val jsonObject = if (jsonElement.isJsonObject) {
                                            jsonElement.asJsonObject
                                        } else {
                                            return@mapNotNull null
                                        }
                                        CargoMetadata.Artifact.fromJson(jsonObject)
                                    } catch (e: JsonSyntaxException) {
                                        null
                                    }
                                }
                                .filter { (target, profile) ->
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
                                .flatMap {
                                    // FIXME: correctly launch binaries for macos
                                    it.filenames.filter { !it.endsWith(".dSYM") }
                                }
                                .let(BuildResult::Binaries)
                        }

                        override fun onSuccess() {
                            when (val result = result!!) {
                                is BuildResult.UnsupportedToolchain -> {
                                    processUnsupportedToolchain(project, promise)
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
        private val PARSER: JsonParser = JsonParser()

        class Binary(val path: Path)

        private sealed class BuildResult {
            data class Binaries(val paths: List<String>) : BuildResult()
            object UnsupportedToolchain : BuildResult()
        }
    }
}
