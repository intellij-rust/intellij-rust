/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.LLDBDriverConfiguration

class RsDebugRunParameters(
    project: Project,
    cmd: GeneralCommandLine
) : RsDebugRunParametersBase(project, cmd) {

    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration {
        val toolchain = CPPToolchains.getInstance().defaultToolchain
        if (toolchain == null || toolchain.isUseLLDB) return LLDBDriverConfiguration()
        return GDBDriverConfiguration(project, toolchain)
    }
}
