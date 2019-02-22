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
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugProcessConfiguratorStarter
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.CPPToolchainsConfigurable
import com.jetbrains.cidr.toolchains.OSType
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.project.workspace.CargoWorkspace.CrateType
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsKillableColoredProcessHandler
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.prependArgument
import org.rust.cargo.util.CargoArgsParser
import org.rust.debugger.settings.RsDebuggerSettings
import java.nio.file.Path
import java.nio.file.Paths

private const val ERROR_MESSAGE_TITLE: String = "Debugging is not possible"

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
        FileDocumentManager.getInstance().saveAllDocuments()
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
                    executableArguments
                )
            }
        }

        return buildProjectAndGetBinaryArtifactPath(environment.project, buildCommand, state, isTestRun)
            .then { binary ->
                if (isTestRun && cmdHasNoRun) return@then null
                val path = binary?.path ?: return@then null
                val runCommand = getRunCommand(path)
                val sysroot = state.computeSysroot()
                val runParameters = RsDebugRunParameters(environment.project, runCommand)
                XDebuggerManager.getInstance(environment.project)
                    .startSession(environment, object : XDebugProcessConfiguratorStarter() {
                        override fun start(session: XDebugSession): XDebugProcess =
                            RsLocalDebugProcess(runParameters, session, state.consoleBuilder).apply {
                                ProcessTerminatedListener.attach(processHandler, environment.project)
                                val settings = RsDebuggerSettings.getInstance()
                                loadPrettyPrinters(sysroot, settings.lldbRenderers, settings.gdbRenderers)
                                start()
                            }

                        override fun configure(data: XDebugSessionData?) {}
                    })
                    .runContentDescriptor
            }

    }
}

private class Binary(val path: Path)

private sealed class DebugBuildResult {
    data class Binaries(val paths: List<String>) : DebugBuildResult()
    object MSVCToolchain : DebugBuildResult()
}

/**
 * Runs `command` twice:
 *   * once to show console with errors
 *   * the second time to get json output
 * See https://github.com/rust-lang/cargo/issues/3855
 */
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
        val toolchains = CPPToolchains.getInstance()
        val toolchain = toolchains.defaultToolchain
        if (toolchain == null) {
            val option = Messages.showDialog(project, "Debug toolchain is not configured.", ERROR_MESSAGE_TITLE,
                arrayOf("Configure"), 0, Messages.getErrorIcon())
            if (option == 0) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, CPPToolchainsConfigurable::class.java, null)
            }
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
                    var result: DebugBuildResult? = null

                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        if (toolchains.osType == OSType.WIN && "msvc" in state.rustVersion().rustc?.host.orEmpty()) {
                            result = DebugBuildResult.MSVCToolchain
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
                                    TargetKind.BIN -> true
                                    TargetKind.EXAMPLE -> {
                                        // TODO: support cases when crate types list contains not only binary
                                        target.cleanCrateTypes.singleOrNull() == CrateType.BIN
                                    }
                                    TargetKind.TEST -> true
                                    TargetKind.LIB -> profile.test
                                    else -> false
                                }
                                isSuitableTarget && (!testsOnly || profile.test)
                            }
                            .flatMap { it.filenames.filter { !it.endsWith(".dSYM") } } // FIXME: correctly launch debug binaries for macos
                            .let(DebugBuildResult::Binaries)
                    }

                    override fun onSuccess() {
                        when (val result = result!!) {
                            DebugBuildResult.MSVCToolchain -> {
                                project.showErrorDialog("MSVC toolchain is not supported for debugging. Please use GNU toolchain.")
                                promise.setResult(null)
                            }
                            is DebugBuildResult.Binaries -> {
                                val binaries = result.paths
                                when {
                                    binaries.isEmpty() -> {
                                        project.showErrorDialog("Can't find a binary to debug")
                                        promise.setResult(null)
                                    }
                                    binaries.size > 1 -> {
                                        project.showErrorDialog("More than one binary produced. " +
                                            "Please specify `--bin`, `--lib`, `--test` or `--example` flag explicitly.")
                                        promise.setResult(null)
                                    }
                                    else -> promise.setResult(Binary(Paths.get(binaries.single())))
                                }
                            }
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
    Messages.showErrorDialog(this, message, ERROR_MESSAGE_TITLE)
}
