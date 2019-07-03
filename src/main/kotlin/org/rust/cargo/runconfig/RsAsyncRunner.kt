/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

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
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.prependArgument
import org.rust.cargo.util.CargoArgsParser
import org.rust.openapiext.saveAllDocuments
import java.nio.file.Path
import java.nio.file.Paths

abstract class RsAsyncRunner(private val executorId: String, private val errorMessageTitle: String) : AsyncProgramRunner<RunnerSettings>() {
    class Binary(val path: Path)
    sealed class BuildResult {
        data class Binaries(val paths: List<String>) : BuildResult()
        object UnsupportedToolchain : BuildResult()
    }

    open fun isApplicable(): Boolean = true

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (!isApplicable()) return false

        if (executorId != this.executorId || profile !is CargoCommandConfiguration) {
            return false
        }
        val cleaned = profile.clean().ok ?: return false
        if (cleaned.cmd.command !in listOf("run", "test")) {
            return false
        }

        return true
    }

    open fun getRunContentDescriptor(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runCommand: GeneralCommandLine
    ): RunContentDescriptor? {
        return showRunContent(executeCmd(state, runCommand, environment), environment)
    }

    private fun executeCmd(state: CargoRunStateBase, cmd: GeneralCommandLine, environment: ExecutionEnvironment): DefaultExecutionResult {
        val runConfiguration = state.runConfiguration
        val context = ConfigurationExtensionContext()

        RsRunConfigurationExtensionManager.getInstance().patchCommandLine(
            runConfiguration,
            environment,
            cmd,
            context
        )

        RsRunConfigurationExtensionManager.getInstance().patchCommandLineState(runConfiguration, environment, state, context)

        val handler = RsKillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination

        RsRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(
            runConfiguration,
            handler,
            environment,
            context
        )

        val console = state.consoleBuilder.console
        console.attachToProcess(handler)
        return DefaultExecutionResult(console, handler)
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        saveAllDocuments()
        state as CargoRunStateBase
        val cmd = Cargo.patchArgs(state.prepareCommandLine(), true)
        val (commandArguments, executableArguments) = CargoArgsParser.parseArgs(cmd.command, cmd.additionalArguments)

        val isTestRun = cmd.command == "test"
        val cmdHasNoRun = "--no-run" in cmd.additionalArguments
        val buildCommand = if (isTestRun) {
            if (cmdHasNoRun) cmd else cmd.prependArgument("--no-run")
        } else {
            cmd.copy(command = "build", additionalArguments = commandArguments)
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

    open fun checkToolchainConfigured(project: Project): Boolean = true

    open fun checkToolchainSupported(state: CargoRunStateBase): Boolean = true

    open fun processUnsupportedToolchain(project: Project, promise: AsyncPromise<Binary?>) {}

    private fun buildProjectAndGetBinaryArtifactPath(
        project: Project,
        command: CargoCommandLine,
        state: CargoRunStateBase,
        testsOnly: Boolean
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
                .apply { state.createFilters().forEach { withFilter(it) } }
                .withAfterCompletion {
                    if (processForUserOutput.exitCode != 0) {
                        promise.setResult(null)
                        return@withAfterCompletion
                    }

                    object : Task.Backgroundable(project, "Building Cargo project") {
                        var result: RsAsyncRunner.BuildResult? = null

                        override fun run(indicator: ProgressIndicator) {
                            indicator.isIndeterminate = true
                            if (!checkToolchainSupported(state)) {
                                result = BuildResult.UnsupportedToolchain
                                return
                            }

                            val processForJson = CapturingProcessHandler(cargo.toGeneralCommandLine(command.prependArgument("--message-format=json")))
                            val output = processForJson.runProcessWithProgressIndicator(indicator)
                            if (output.isCancelled || output.exitCode != 0) {
                                promise.setResult(null)
                                return
                            }

                            val parser = JsonParser()
                            result = output.stdoutLines
                                .mapNotNull {
                                    try {
                                        val jsonElement = parser.parse(it)
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
                                    isSuitableTarget && (!testsOnly || profile.test)
                                }
                                .flatMap { it.filenames.filter { !it.endsWith(".dSYM") } } // FIXME: correctly launch binaries for macos
                                .let(RsAsyncRunner.BuildResult::Binaries)
                        }

                        override fun onSuccess() {
                            when (val result = result!!) {
                                is RsAsyncRunner.BuildResult.UnsupportedToolchain -> {
                                    processUnsupportedToolchain(project, promise)
                                }
                                is RsAsyncRunner.BuildResult.Binaries -> {
                                    val binaries = result.paths
                                    when {
                                        binaries.isEmpty() -> {
                                            project.showErrorDialog("Can't find a binary")
                                            promise.setResult(null)
                                        }
                                        binaries.size > 1 -> {
                                            project.showErrorDialog("More than one binary produced. " +
                                                "Please specify `--bin`, `--lib`, `--test` or `--example` flag explicitly.")
                                            promise.setResult(null)
                                        }
                                        else -> promise.setResult(RsAsyncRunner.Binary(Paths.get(binaries.single())))
                                    }
                                }
                            }
                        }

                        override fun onThrowable(error: Throwable) {
                            promise.setResult(null)
                        }
                    }.queue()
                }.run()
        }

        return promise
    }

    protected fun Project.showErrorDialog(message: String) {
        Messages.showErrorDialog(this, message, errorMessageTitle)
    }
}
