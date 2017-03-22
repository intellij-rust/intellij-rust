package org.rust.debugger.runconfig

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.execution.RunProfileStarter
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.AsyncGenericProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
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
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.util.cargoProjectRoot

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
            config.cargoCommandLine.copy(additionalArguments = listOf("--message-format=json"), command = "build")
        } else {
            check(config.cargoCommandLine.command == "test")
            config.cargoCommandLine.copy(additionalArguments = listOf("--no-run", "--message-format=json")
                + config.cargoCommandLine.additionalArguments)
        }

        val task = BuildCargoBinaryTask(project, cargo.generalCommand(buildCommand))
        runWriteAction {
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        return task.queueAsync()
            .then { binary -> RsRunProfileStarter(GeneralCommandLine(binary)) }
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


private class BuildCargoBinaryTask(
    project: Project,
    val cmd: GeneralCommandLine
) : Task.Backgroundable(project, "Building Cargo project") {
    private val promise: AsyncPromise<String> = AsyncPromise()

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        val process = CapturingProcessHandler(cmd)

        val output = process.runProcessWithProgressIndicator(indicator)
        if (output.isCancelled && output.exitCode != 0) {
            error("Failed to build crate: ${cmd.commandLineString}")
        }

        val parser = JsonParser()
        val gson = Gson()
        val binary = output.stdoutLines
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
            .singleOrNull()

        if (binary != null) {
            promise.setResult(binary)
        } else {
            promise.setError("Can't find a single binary to debug")
        }
    }

    override fun onSuccess() {}

    override fun onError(error: Exception) {
        promise.setError(error)
    }

    fun queueAsync(): Promise<String> {
        queue()
        return promise
    }
}
