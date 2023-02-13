/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.cidr.cpp.execution.debugger.backend.CLionLLDBDriverConfiguration
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.createDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import org.rust.debugger.RsDebuggerDriverConfigurationProvider
import org.rust.debugger.isNewGdbSetupEnabled

class RsCLionDebuggerDriverConfigurationProvider : RsDebuggerDriverConfigurationProvider {
    override fun getDebuggerDriverConfiguration(
        project: Project,
        isElevated: Boolean,
        emulateTerminal: Boolean
    ): DebuggerDriverConfiguration? {
        // Delegate to `RsDefaultDebuggerDriverConfigurationProvider`
        if (isNewGdbSetupEnabled) return null

        val toolchain = CPPToolchains.getInstance().defaultToolchain ?: return null
        val isLLDBRustMSVCSupportEnabled = Registry.`is`("org.rust.debugger.lldb.rust.msvc", false)

        return if (toolchain.toolSet.isMSVC && isLLDBRustMSVCSupportEnabled) {
            object : CLionLLDBDriverConfiguration(project, CPPEnvironment(toolchain), isElevated, emulateTerminal) {
                override fun useRustTypeSystem(): Boolean = true
            }
        } else {
            createDriverConfiguration(project, CPPEnvironment(toolchain), isElevated, emulateTerminal)
        }
    }
}
