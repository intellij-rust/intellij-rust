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

abstract class RsDebugRunParametersBase(
    val project: Project,
    val cmd: GeneralCommandLine
) : RunParameters() {
    override fun getInstaller(): Installer = TrivialInstaller(cmd)
    override fun getArchitectureId(): String? = null
}
