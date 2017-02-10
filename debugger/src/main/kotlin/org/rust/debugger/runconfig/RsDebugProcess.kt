package org.rust.debugger.runconfig

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration
import com.jetbrains.cidr.execution.Installer
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialInstaller
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrBreakpointHandler
import org.rust.debugger.RsLineBreakpointType

class RsDebugProcess(parameters: RunParameters, session: XDebugSession, consoleBuilder: TextConsoleBuilder)
    : CidrLocalDebugProcess(parameters, session, consoleBuilder) {

    override fun createBreakpointHandler(): CidrBreakpointHandler =
        CidrBreakpointHandler(this, RsLineBreakpointType::class.java)
}

class RsDebugCommandLineState(
    environment: ExecutionEnvironment,
    private val cmd: GeneralCommandLine
) : CommandLineState(environment) {
    @Throws(ExecutionException::class)
    override fun startProcess(): ProcessHandler {
        error("supports only debug process")
    }

    @Throws(ExecutionException::class)
    fun startDebugProcess(session: XDebugSession): CidrDebugProcess =
        RsDebugProcess(RsDebugRunParameters(cmd), session, consoleBuilder).apply {
            ProcessTerminatedListener.attach(processHandler, environment.project)
            start()
        }
}

class RsDebugRunParameters(
    val cmd: GeneralCommandLine
) : RunParameters() {
    private val cfg = GDBDriverConfiguration()

    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration = cfg

    override fun getInstaller(): Installer = TrivialInstaller(cmd)

    override fun getArchitectureId(): String? = null
}


