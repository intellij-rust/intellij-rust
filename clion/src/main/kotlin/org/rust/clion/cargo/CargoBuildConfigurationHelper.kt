/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrBuildConfigurationHelper
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin

class CargoBuildConfigurationHelper(private val project: Project)
    : CidrBuildConfigurationHelper<CLionCargoBuildConfiguration, CargoBuildTarget>() {

    override fun getTargets(): List<CargoBuildTarget> {
        val result = mutableListOf<CargoBuildTarget>()
        for (cargoProject in project.cargoProjects.allProjects) {
            val workspace = cargoProject.workspace ?: continue
            for (pkg in workspace.packages) {
                if (pkg.origin != PackageOrigin.WORKSPACE) continue
                for (target in pkg.targets) {
                    result.add(CargoBuildTarget(project, target))
                }
            }
        }
        return result
    }

    override fun allowEditBuildConfiguration(): Boolean = false
}
