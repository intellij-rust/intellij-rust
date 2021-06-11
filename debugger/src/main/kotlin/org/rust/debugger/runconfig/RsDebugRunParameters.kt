/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.Installer
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialInstaller
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import org.rust.cargo.project.model.CargoProject
import org.rust.debugger.RsDebuggerDriverConfigurationProvider

class RsDebugRunParameters(
    val project: Project,
    private val cmd: GeneralCommandLine,
    val cargoProject: CargoProject?,
    private val isElevated: Boolean
) : RunParameters() {

    override fun getInstaller(): Installer = TrivialInstaller(cmd)
    override fun getArchitectureId(): String? = null

    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration {
        for (provider in RsDebuggerDriverConfigurationProvider.EP_NAME.extensionList) {
            return provider.getDebuggerDriverConfiguration(project, isElevated) ?: continue
        }
        return object : LLDBDriverConfiguration() {
            override fun isElevated(): Boolean = isElevated
        }
    }
}
