/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet
import com.jetbrains.cidr.cpp.toolchains.createDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import org.rust.debugger.RsDebuggerDriverConfigurationProvider

class RsCLionDebuggerDriverConfigurationProvider : RsDebuggerDriverConfigurationProvider {
    override fun getDebuggerDriverConfiguration(project: Project): DebuggerDriverConfiguration? {
        val toolchains = CPPToolchains.getInstance()
        val toolchain = if (!SystemInfo.isWindows) {
            toolchains.defaultToolchain
        } else {
            toolchains.toolchains.firstOrNull { it.toolSetKind == CPPToolSet.Kind.MINGW }
        } ?: return null

        return createDriverConfiguration(project, CPPEnvironment(toolchain))
    }
}
