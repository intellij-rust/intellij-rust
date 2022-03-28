/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import java.io.File

class RsDefaultDebuggerDriverConfigurationProvider : RsDebuggerDriverConfigurationProvider {
    override fun getDebuggerDriverConfiguration(project: Project, isElevated: Boolean): DebuggerDriverConfiguration? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val lldbStatus = RsDebuggerToolchainService.getInstance().getLLDBStatus()
        return when (lldbStatus) {
            LLDBStatus.Bundled -> RsLLDBDriverConfiguration(isElevated)
            is LLDBStatus.Binaries -> RsCustomBinariesLLDBDriverConfiguration(lldbStatus, isElevated)
            else -> null
        }
    }
}

private open class RsLLDBDriverConfiguration(
    private val isElevated: Boolean
) : LLDBDriverConfiguration() {
    override fun isElevated(): Boolean = isElevated
}

private class RsCustomBinariesLLDBDriverConfiguration(
    private val binaries: LLDBStatus.Binaries,
    isElevated: Boolean
) : RsLLDBDriverConfiguration(isElevated) {
    override fun getDriverName(): String = "Rust LLDB"
    override fun useSTLRenderers(): Boolean = false
    override fun getLLDBFrameworkFile(architectureType: ArchitectureType): File = binaries.frameworkFile
    override fun getLLDBFrontendFile(architectureType: ArchitectureType): File = binaries.frontendFile
}
