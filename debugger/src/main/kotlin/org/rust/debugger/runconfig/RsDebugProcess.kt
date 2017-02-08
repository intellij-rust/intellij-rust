package org.rust.debugger.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.Installer
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialInstaller
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrBreakpointHandler
import com.jetbrains.cidr.execution.testing.CidrLauncher
import org.rust.debugger.RsLineBreakpointType

class RsDebugProcess(parameters: RunParameters, session: XDebugSession, consoleBuilder: TextConsoleBuilder)
    : CidrLocalDebugProcess(parameters, session, consoleBuilder) {

    override fun createBreakpointHandler(): CidrBreakpointHandler =
        CidrBreakpointHandler(this, RsLineBreakpointType::class.java)
}

class RsDebugRunState(
    environment: ExecutionEnvironment,
    launcher: CidrLauncher
) : CidrCommandLineState(environment, launcher)

class RsDebugLauncher(
    val _project: Project,
    val cl: GeneralCommandLine
) : CidrLauncher() {
    override fun getProject(): Project = _project

    override fun createDebugProcess(state: CommandLineState, session: XDebugSession): CidrDebugProcess {
        return RsDebugProcess(RsDebugRunParameters(cl), session, state.consoleBuilder)
    }

    override fun createProcess(state: CommandLineState): ProcessHandler {
        TODO()
    }
}

class RsDebugRunParameters(
    val cl: GeneralCommandLine
) : RunParameters() {
    private val cfg = GDBDriverConfiguration()

    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration = cfg

    override fun getInstaller(): Installer = TrivialInstaller(cl)

    override fun getArchitectureId(): String? = null
}


