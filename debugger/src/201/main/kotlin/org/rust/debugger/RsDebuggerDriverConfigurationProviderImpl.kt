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

class RsDebuggerDriverConfigurationProviderImpl : RsDebuggerDriverConfigurationProvider {
    override fun getDebuggerDriverConfiguration(project: Project): DebuggerDriverConfiguration? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val lldbStatus = RsDebuggerToolchainService.getInstance().getLLDBStatus()
        return when (lldbStatus) {
            is LLDBStatus.Binaries -> RsLLDBDriverConfiguration(lldbStatus)
            else -> null
        }
    }
}

private class RsLLDBDriverConfiguration(
    private val binaries: LLDBStatus.Binaries
) : LLDBDriverConfiguration() {

    override fun getDriverName(): String = "Rust LLDB"

    override fun getLLDBFrameworkFile(architectureType: ArchitectureType): File {
        return binaries.frameworkFile
    }

    override fun getLLDBFrontendFile(architectureType: ArchitectureType): File {
        return binaries.frontendFile
    }
}
