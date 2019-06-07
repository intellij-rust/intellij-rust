/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.build.CLionBuildConfigurationProvider
import com.jetbrains.cidr.execution.CidrBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class CargoBuildConfigurationProvider : CLionBuildConfigurationProvider {

    override fun getBuildableConfigurations(project: Project): List<CidrBuildConfiguration> {
        val runConfiguration = RunManager.getInstance(project).selectedConfiguration?.configuration
        return if (runConfiguration is CargoCommandConfiguration) listOf(CargoBuildConfiguration()) else emptyList()
    }
}
