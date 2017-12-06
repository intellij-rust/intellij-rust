/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.*
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugProcessConfiguratorStarter
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.withWorkDirectory
import java.nio.file.Path
import java.nio.file.Paths

class RsDebugRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "RsDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultDebugExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration) {
            return false
        }
        val cleaned = profile.clean().ok ?: return false
        if (cleaned.cmd.command !in listOf("run", "test")) {
            return false
        }

        return true
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        state as CargoRunState
        val cmd = state.commandLine

        val (buildArgs, execArgs) = cmd.splitOnDoubleDash()

        val buildCommand = if (cmd.command == "run") {
            cmd.copy(command = "build", additionalArguments = buildArgs)
        } else {
            check(cmd.command == "test")
            cmd.prependArgument("--no-run")
        }

        val runCommand = { executablePath: Path ->
            GeneralCommandLine(executablePath)
                .withWorkDirectory(cmd.workingDirectory)
                .withParameters(execArgs)
        }

        return buildProjectAndGetBinaryArtifactPath(environment.project, buildCommand, state.cargo())
            .then { binary ->
                val path = binary?.path ?: return@then null
                val commandLine = runCommand(path)
                check(commandLine.workDirectory != null) {
                    "LLDB requires working directory"
                }
                val runParameters = RsDebugRunParameters(environment.project, commandLine)
                XDebuggerManager.getInstance(environment.project)
                    .startSession(environment, object : XDebugProcessConfiguratorStarter() {
                        override fun start(session: XDebugSession): XDebugProcess =
                            RsDebugProcess(runParameters, session, state.consoleBuilder).apply {
                                ProcessTerminatedListener.attach(processHandler, environment.project)
                                start()
                            }

                        override fun configure(data: XDebugSessionData?) {}
                    })
                    .runContentDescriptor
            }

    }
}

private class Binary(val path: Path)

/**
 * Runs `command` twice:
 *   * once to show console with errors
 *   * the second time to get json output
 * See https://github.com/rust-lang/cargo/issues/3855
 */
private fun buildProjectAndGetBinaryArtifactPath(project: Project, command: CargoCommandLine, cargo: Cargo): Promise<Binary?> {
    val promise = AsyncPromise<Binary?>()

    val processForUserOutput = ProcessOutput()
    val processForUser = KillableColoredProcessHandler(cargo.toColoredCommandLine(command))

    processForUser.addProcessListener(CapturingProcessAdapter(processForUserOutput))

    ApplicationManager.getApplication().invokeLater {
        RunContentExecutor(project, processForUser)
            .withAfterCompletion {
                if (processForUserOutput.exitCode != 0) {
                    promise.setResult(null)
                    return@withAfterCompletion
                }

                object : Task.Backgroundable(project, "Building Cargo project") {
                    var result: List<String>? = null

                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
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
                                    parser.parse(it)
                                } catch (e: JsonSyntaxException) {
                                    null
                                }
                            }
                            .mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                            .mapNotNull { CargoMetadata.Artifact.fromJson(it) }
                            .filter { (target, profile) ->
                                target.cleanKind == CargoWorkspace.TargetKind.BIN
                                    || target.cleanKind == CargoWorkspace.TargetKind.TEST
                                    || (target.cleanKind == CargoWorkspace.TargetKind.LIB && profile.test)
                            }
                            .flatMap { it.filenames.filter { !it.endsWith(".dSYM") } } // FIXME: correctly launch debug binaries for macos
                    }

                    override fun onSuccess() {
                        val binaries = result!!
                        when {
                            binaries.isEmpty() -> {
                                project.showErrorDialog("Can't find a binary to debug")
                                promise.setResult(null)
                            }
                            binaries.size > 1 -> {
                                project.showErrorDialog("More than one binary produced. " +
                                    "Please specify `--bin`, `--lib` or `--test` flag explicitly.")
                                promise.setResult(null)
                            }
                            else -> promise.setResult(Binary(Paths.get(binaries.single())))
                        }
                    }

                    override fun onThrowable(error: Throwable) {
                        promise.setResult(null)
                    }
                }.queue()
            }
            .run()
    }

    return promise
}

private fun Project.showErrorDialog(message: String) {
    Messages.showErrorDialog(this, message, "Debugging is not possible")
}


private fun CargoCommandLine.prependArgument(arg: String): CargoCommandLine =
    copy(additionalArguments = listOf(arg) + additionalArguments)
