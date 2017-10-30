/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.execution.Installer
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialInstaller
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.LLDBDriverConfiguration
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrBreakpointHandler
import org.rust.debugger.RsLineBreakpointType

class RsDebugProcess(parameters: RunParameters, session: XDebugSession, consoleBuilder: TextConsoleBuilder)
    : CidrLocalDebugProcess(parameters, session, consoleBuilder) {

    override fun createBreakpointHandler(): CidrBreakpointHandler =
        CidrBreakpointHandler(this, RsLineBreakpointType::class.java)
}

class RsDebugRunParameters(
    val project: Project,
    val cmd: GeneralCommandLine
) : RunParameters() {
    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration {
        val toolchain = CPPToolchains.getInstance().defaultToolchain
        if (toolchain == null || toolchain.isUseLLDB) return LLDBDriverConfiguration()
        return GDBDriverConfiguration(project, toolchain)
    }
    override fun getInstaller(): Installer = TrivialInstaller(cmd)

    override fun getArchitectureId(): String? = null

}


