package org.rust.debugger.runconfig

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.RunProfileStarter
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.*
import com.intellij.execution.runners.AsyncGenericProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SelectFromListDialog
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugProcessConfiguratorStarter
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.util.cargoProjectRoot
import javax.swing.ListSelectionModel

class RsDebugRunner : AsyncGenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "RsDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultDebugExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration) {
            return false
        }
        if (profile.cargoCommandLine.command !in listOf("run", "test")) {
            return false
        }

        return true
    }

    override fun prepare(env: ExecutionEnvironment, state: RunProfileState): Promise<RunProfileStarter> {
        val config = env.runnerAndConfigurationSettings!!.configuration as CargoCommandConfiguration
        val project = config.project
        val cargo = project.toolchain!!.cargo(config.configurationModule.module!!.cargoProjectRoot!!.path)

        val buildCommand = if (config.cargoCommandLine.command == "run") {
            config.cargoCommandLine.copy(command = "build")
        } else {
            check(config.cargoCommandLine.command == "test")
            config.cargoCommandLine.prependArgument("--no-run")
        }

        return buildProjectAndGetBinaryArtifactPath(config.configurationModule.module!!, buildCommand, cargo)
            .then { result ->
                result?.path?.let { RsRunProfileStarter(GeneralCommandLine(it)) }
            }
    }
}

private class RsRunProfileStarter(val commandLine: GeneralCommandLine) : RunProfileStarter() {
    override fun execute(state: RunProfileState, env: ExecutionEnvironment): RunContentDescriptor {
        state as CargoRunState
        val runParameters = RsDebugRunParameters(commandLine)
        val debugSession = XDebuggerManager.getInstance(env.project)
            .startSession(env, object : XDebugProcessConfiguratorStarter() {

                override fun start(session: XDebugSession): XDebugProcess =
                    RsDebugProcess(runParameters, session, state.consoleBuilder).apply {
                        ProcessTerminatedListener.attach(processHandler, env.project)
                        start()
                    }

                override fun configure(data: XDebugSessionData?) {}
            })

        return debugSession.runContentDescriptor
    }
}


private class Binary(val path: String)

/**
 * Runs `command` twice:
 *   * once to show console with errors
 *   * the second time to get json output
 * See https://github.com/rust-lang/cargo/issues/3855
 */
private fun buildProjectAndGetBinaryArtifactPath(module: Module, command: CargoCommandLine, cargo: Cargo): Promise<Binary?> {
    val result = AsyncPromise<Binary?>()

    val processForUserOutput = ProcessOutput()
    val processForUser = KillableColoredProcessHandler(cargo.generalCommand(command))
    val processForJson = CapturingProcessHandler(cargo.generalCommand(command.prependArgument("--message-format=json")))

    processForUser.addProcessListener(CapturingProcessAdapter(processForUserOutput))

    ApplicationManager.getApplication().invokeLater {
        RunContentExecutor(module.project, processForUser)
            .withAfterCompletion {
                if (processForUserOutput.exitCode != 0) {
                    result.setResult(null)
                    return@withAfterCompletion
                }

                object : Task.Backgroundable(module.project, "Building Cargo project") {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        val output = processForJson.runProcessWithProgressIndicator(indicator)
                        if (output.isCancelled || output.exitCode != 0) {
                            result.setResult(null)
                            return
                        }

                        val parser = JsonParser()
                        val gson = Gson()
                        val binaries = output.stdoutLines
                            .mapNotNull {
                                try {
                                    parser.parse(it)
                                } catch(e: JsonSyntaxException) {
                                    null
                                }
                            }
                            .mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                            .filter { it.getAsJsonPrimitive("reason").asString == "compiler-artifact" }
                            .filter { "bin" in gson.fromJson(it.getAsJsonObject("target"), CargoMetadata.Target::class.java).kind }
                            .flatMap { it.getAsJsonArray("filenames").map { it.asString } }

                        if (binaries.isEmpty()) {
                            project.showErrorDialog("Can't find a binary to debug")
                            result.setResult(null)
                            return
                        }

                        if (binaries.size == 1) {
                            result.setResult(Binary(binaries.single()))
                            return
                        }

                        ApplicationManager.getApplication().invokeAndWait {
                            val dialog = SelectFromListDialog(project,
                                binaries.toTypedArray(),
                                SelectFromListDialog.ToStringAspect { it as String },
                                "Select binary to debug",
                                ListSelectionModel.SINGLE_SELECTION
                            )
                            result.setResult(
                                if (dialog.showAndGet()) Binary(dialog.selection[0] as String) else null
                            )
                        }
                    }
                }.queue()
            }
            .run()
    }

    return result
}

private fun Project.showErrorDialog(message: String) {
    Messages.showErrorDialog(this, "Debugging is not possible", message)
}


private fun CargoCommandLine.prependArgument(arg: String): CargoCommandLine =
    copy(additionalArguments = listOf(arg) + additionalArguments)
