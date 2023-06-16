/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import org.rust.debugger.settings.RsDebuggerSettings
import java.io.File

class RsDefaultDebuggerDriverConfigurationProvider : RsDebuggerDriverConfigurationProvider {
    @Suppress("MoveVariableDeclarationIntoWhen")
    override fun getDebuggerDriverConfiguration(project: Project, isElevated: Boolean): DebuggerDriverConfiguration? {
        val debuggerKind  = RsDebuggerSettings.getInstance().debuggerKind
        when (debuggerKind) {
            DebuggerKind.LLDB -> {
                val lldbAvailability = RsDebuggerToolchainService.getInstance().lldbAvailability()
                return when (lldbAvailability) {
                    DebuggerAvailability.Bundled -> RsLLDBDriverConfiguration(isElevated)
                    is DebuggerAvailability.Binaries -> RsCustomBinariesLLDBDriverConfiguration(lldbAvailability.binaries, isElevated)
                    else -> null
                }
            }
            DebuggerKind.GDB -> {
                val gdbAvailability = RsDebuggerToolchainService.getInstance().gdbAvailability()
                return when (gdbAvailability) {
                    DebuggerAvailability.Bundled -> RsGDBDriverConfiguration(isElevated)
                    else -> null
                }
            }
        }
    }
}

class RsGDBDriverConfiguration(
    private val isElevated: Boolean
) : GDBDriverConfiguration() {

    override fun getDriverName(): String {
        return "Rust GDB"
    }

    // TODO: investigate attach to process feature separately
    override fun isAttachSupported(): Boolean = false
    override fun isElevated(): Boolean = isElevated
}

open class RsLLDBDriverConfiguration(
    private val isElevated: Boolean
) : LLDBDriverConfiguration() {
    override fun isElevated(): Boolean = isElevated

    override fun useRustTypeSystem(): Boolean =
        SystemInfo.isWindows && Registry.`is`("org.rust.debugger.lldb.rust.msvc", false)
}

private class RsCustomBinariesLLDBDriverConfiguration(
    private val binaries: LLDBBinaries,
    isElevated: Boolean
) : RsLLDBDriverConfiguration(isElevated) {
    override fun getDriverName(): String = "Rust LLDB"
    override fun useSTLRenderers(): Boolean = false
    override fun getLLDBFrameworkFile(architectureType: ArchitectureType): File = binaries.frameworkFile
    override fun getLLDBFrontendFile(architectureType: ArchitectureType): File = binaries.frontendFile
}
