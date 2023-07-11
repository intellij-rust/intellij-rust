/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.removeUserData
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriver
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import org.rust.debugger.settings.RsDebuggerSettings
import java.io.File

class RsDefaultDebuggerDriverConfigurationProvider : RsDebuggerDriverConfigurationProvider {
    @Suppress("MoveVariableDeclarationIntoWhen")
    override fun getDebuggerDriverConfiguration(
        project: Project,
        isElevated: Boolean,
        emulateTerminal: Boolean
    ): DebuggerDriverConfiguration? {
        val debuggerKind = RsDebuggerSettings.getInstance().debuggerKind
        when (debuggerKind) {
            DebuggerKind.LLDB -> {
                val lldbAvailability = RsDebuggerToolchainService.getInstance().lldbAvailability()
                return when (lldbAvailability) {
                    DebuggerAvailability.Bundled -> RsLLDBDriverConfiguration(isElevated, emulateTerminal)
                    is DebuggerAvailability.Binaries -> RsCustomBinariesLLDBDriverConfiguration(lldbAvailability.binaries, isElevated, emulateTerminal)
                    else -> null
                }
            }

            DebuggerKind.GDB -> {
                val gdbAvailability = RsDebuggerToolchainService.getInstance().gdbAvailability()
                return when (gdbAvailability) {
                    DebuggerAvailability.Bundled -> RsGDBDriverConfiguration(isElevated, emulateTerminal)
                    is DebuggerAvailability.Binaries -> RsCustomBinariesGDBDriverConfiguration(gdbAvailability.binaries, isElevated, emulateTerminal)
                    else -> null
                }
            }
        }
    }
}

open class RsGDBDriverConfiguration(
    private val isElevated: Boolean,
    private val emulateTerminal: Boolean
) : GDBDriverConfiguration() {
    override fun getDriverName(): String = "Rust GDB"

    // TODO: investigate attach to process feature separately
    override fun isAttachSupported(): Boolean = false
    override fun isElevated(): Boolean = isElevated
    override fun emulateTerminal(): Boolean = emulateTerminal
}

private class RsCustomBinariesGDBDriverConfiguration(
    private val binaries: GDBBinaries,
    isElevated: Boolean,
    emulateTerminal: Boolean
) : RsGDBDriverConfiguration(isElevated, emulateTerminal) {
    override fun getGDBExecutablePath(): String = binaries.gdbFile.toString()

    override fun createDriverCommandLine(driver: DebuggerDriver, architectureType: ArchitectureType): GeneralCommandLine {
        val cmd = super.createDriverCommandLine(driver, architectureType)
        // `GDBDriverConfiguration` assumes that there are bundled C++ pretty-printers.
        // It's not true in the case of Native Debugging Support plugin outside CLion,
        // and gdb throws an exception `Error while executing Python code`.
        // As a result, Rust pretty printers are not loaded as well.
        // As a temporary workaround, just remove paths to C++ pretty printers.
        // See https://youtrack.jetbrains.com/issue/CPP-34231
        cmd.removeUserData(GDBDriver.PRETTY_PRINTERS_PATH)
        cmd.removeUserData(GDBDriver.ENABLE_STL_PRETTY_PRINTERS)
        return cmd
    }
}

open class RsLLDBDriverConfiguration(
    private val isElevated: Boolean,
    private val emulateTerminal: Boolean
) : LLDBDriverConfiguration() {
    override fun isElevated(): Boolean = isElevated
    override fun emulateTerminal(): Boolean = emulateTerminal
    override fun useRustTypeSystem(): Boolean =
        SystemInfo.isWindows && Registry.`is`("org.rust.debugger.lldb.rust.msvc", false)
}

private class RsCustomBinariesLLDBDriverConfiguration(
    private val binaries: LLDBBinaries,
    isElevated: Boolean,
    emulateTerminal: Boolean
) : RsLLDBDriverConfiguration(isElevated, emulateTerminal) {
    override fun getDriverName(): String = "Rust LLDB"
    override fun useSTLRenderers(): Boolean = false
    override fun getLLDBFrameworkFile(architectureType: ArchitectureType): File = binaries.frameworkFile.toFile()
    override fun getLLDBFrontendFile(architectureType: ArchitectureType): File = binaries.frontendFile.toFile()
}
