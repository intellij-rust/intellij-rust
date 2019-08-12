/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.jetbrains.cidr.execution.CidrBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

@Suppress("UnstableApiUsage")
class CLionCargoBuildConfiguration(configuration: CargoCommandConfiguration, environment: ExecutionEnvironment)
    : CargoBuildConfiguration(configuration, environment), CidrBuildConfiguration {
    override val enabled: Boolean get() = true

    override fun getName(): String = "Cargo Build"

    override fun getExternalSource(): ProjectModelExternalSource? = super<CargoBuildConfiguration>.getExternalSource()
}
