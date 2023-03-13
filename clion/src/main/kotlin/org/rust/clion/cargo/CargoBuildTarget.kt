/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrBuildTarget
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.toolwindow.launchCommand
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.buildtool.CargoBuildManager
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import javax.swing.Icon

class CargoBuildTarget(
    private val project: Project,
    private val target: CargoWorkspace.Target
) : CidrBuildTarget<CLionCargoBuildConfiguration> {

    override fun getName(): String {
        val name = target.name
        val kind = when (target.kind) {
            is CargoWorkspace.TargetKind.Lib -> "lib"
            CargoWorkspace.TargetKind.Bin -> "bin"
            CargoWorkspace.TargetKind.Test -> "test"
            CargoWorkspace.TargetKind.Bench -> "bench"
            CargoWorkspace.TargetKind.ExampleBin, is CargoWorkspace.TargetKind.ExampleLib -> "example"
            CargoWorkspace.TargetKind.CustomBuild -> "build.rs"
            else -> ""
        }
        return "$name ($kind)"
    }

    override fun getProjectName(): String = target.pkg.name

    override fun getIcon(): Icon? = when (target.kind) {
        is CargoWorkspace.TargetKind.Lib -> CargoIcons.LIB_TARGET
        CargoWorkspace.TargetKind.Bin -> CargoIcons.BIN_TARGET
        CargoWorkspace.TargetKind.Test -> CargoIcons.TEST_TARGET
        CargoWorkspace.TargetKind.Bench -> CargoIcons.BENCH_TARGET
        CargoWorkspace.TargetKind.ExampleBin, is CargoWorkspace.TargetKind.ExampleLib -> CargoIcons.EXAMPLE_TARGET
        CargoWorkspace.TargetKind.CustomBuild -> CargoIcons.CUSTOM_BUILD_TARGET
        CargoWorkspace.TargetKind.Unknown -> null
    }

    override fun isExecutable(): Boolean = when (target.kind) {
        CargoWorkspace.TargetKind.Bin,
        CargoWorkspace.TargetKind.Test,
        CargoWorkspace.TargetKind.Bench,
        CargoWorkspace.TargetKind.ExampleBin -> true
        else -> false
    }

    override fun getBuildConfigurations(): List<CLionCargoBuildConfiguration> {
        val command = target.launchCommand() ?: return emptyList()
        val commandLine = CargoCommandLine.forTarget(target, command)
        val runnerAndConfiguration = RunManager.getInstance(project).createCargoCommandRunConfiguration(commandLine)
        val configuration = runnerAndConfiguration.configuration as? CargoCommandConfiguration ?: return emptyList()
        val buildConfiguration = CargoBuildManager.getBuildConfiguration(configuration) ?: return emptyList()
        val environment = CargoBuildManager.createBuildEnvironment(buildConfiguration) ?: return emptyList()
        return listOf(CLionCargoBuildConfiguration(buildConfiguration, environment))
    }

    override fun toString(): String = "$name ($projectName)"
}
