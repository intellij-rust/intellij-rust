/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.CidrDebugProcessConfigurator
import org.rust.cargo.project.model.cargoProjects

class RsDebugProcessConfigurator : CidrDebugProcessConfigurator {
    override fun configure(process: CidrDebugProcess) {
        val cargoProject = when {
            process is RsLocalDebugProcess -> {
                // In case of Rust project, select the corresponding Cargo project
                process.runParameters.cargoProject
            }
            process.project.cargoProjects.hasAtLeastOneValidProject -> {
                // In case of cross-language project (e.g. C project with some Rust code inside),
                // we actually don't know which Cargo project will be used during execution.
                // So any of the available Rust projects can be selected
                process.project.cargoProjects.allProjects.firstOrNull()
            }
            else -> {
                // Otherwise, don't configure the debug process for Rust
                return
            }
        }
        RsDebugProcessConfigurationHelper(process, cargoProject).configure()
    }
}
