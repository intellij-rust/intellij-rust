package org.rust.debugger.runconfig

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunProfileStarter
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.runners.AsyncGenericProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugProcessConfiguratorStarter
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.jetbrains.cidr.execution.CidrCommandLineState
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.cargoProjectRoot

class RsDebugRunner : AsyncGenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "RsDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is CargoDebugConfiguration
    }

    override fun prepare(env: ExecutionEnvironment, state: RunProfileState): Promise<RunProfileStarter> {
        val config = env.runnerAndConfigurationSettings!!.configuration as CargoDebugConfiguration
        val project = config.project
        val cargo = project.toolchain!!.cargo(config.configurationModule.module!!.cargoProjectRoot!!.path)

        val cmd = cargo.generalCommand("build", listOf("--bin", config.binary))
        val task = BuildCargoBinaryTask(project, cmd)
        return task.queueAsync().then {
            object : RunProfileStarter() {
                override fun execute(state: RunProfileState, env: ExecutionEnvironment): RunContentDescriptor? {
                    return startDebugSession(state as CidrCommandLineState, env, false).runContentDescriptor
                }
            }
        }
    }

    @Throws(ExecutionException::class)
    fun startDebugSession(state: CidrCommandLineState,
                          env: ExecutionEnvironment,
                          muteBreakpoints: Boolean,
                          vararg listeners: XDebugSessionListener): XDebugSession {
        return XDebuggerManager.getInstance(env.project).startSession(env, object : XDebugProcessConfiguratorStarter() {
            @Throws(ExecutionException::class)
            override fun start(session: XDebugSession): XDebugProcess {
                for (l in listeners) {
                    session.addSessionListener(l)
                }
                return state.startDebugProcess(session)
            }

            override fun configure(data: XDebugSessionData) {
                if (muteBreakpoints) {
                    data.isBreakpointsMuted = true
                }
            }
        })
    }
}


class BuildCargoBinaryTask(
    project: Project,
    val cmd: GeneralCommandLine
) : Task.Backgroundable(project, "Building Cargo project") {
    private val promise: AsyncPromise<Unit> = AsyncPromise()

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        val process = CapturingProcessHandler(cmd)
        val output = process.runProcessWithProgressIndicator(indicator)
        if (output.isCancelled && output.exitCode != 0) {
            error("Failed to build caret: ${cmd.commandLineString}")
        }
    }

    override fun onSuccess() {
        promise.setResult(Unit)
    }

    override fun onError(error: Exception) {
        promise.setError(error)
    }

    fun queueAsync(): Promise<Unit> {
        queue()
        return promise
    }
}
