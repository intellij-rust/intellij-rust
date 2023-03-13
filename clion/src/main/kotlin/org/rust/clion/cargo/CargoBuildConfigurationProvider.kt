/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.execution.RunManager
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.CidrRunnerBundle
import com.jetbrains.cidr.cpp.execution.compound.CidrCompoundRunConfiguration
import com.jetbrains.cidr.execution.BuildConfigurationProblems
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData
import com.jetbrains.cidr.execution.build.CidrBuildConfigurationProvider
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.createBuildEnvironment
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class CargoBuildConfigurationProvider : CidrBuildConfigurationProvider {
    override fun getBuildableConfigurations(project: Project): List<CLionCargoBuildConfiguration> {
        val runManager = RunManager.getInstance(project) as? RunManagerImpl ?: return emptyList()
        val buildConfiguration = when (val configuration = runManager.selectedConfiguration?.configuration) {
            is CargoCommandConfiguration -> {
                val buildConfiguration = getBuildConfiguration(configuration) ?: return emptyList()
                val environment = createBuildEnvironment(buildConfiguration) ?: return emptyList()
                CLionCargoBuildConfiguration(buildConfiguration, environment)
            }

            is CidrCompoundRunConfiguration -> getCidrBuildConfiguration(project, configuration)
            else -> null
        } ?: return emptyList()
        return listOf(buildConfiguration)
    }
}

fun getCidrBuildConfiguration(project: Project, runConfig: CidrCompoundRunConfiguration): CLionCargoBuildConfiguration? {
    val problems = BuildConfigurationProblems()
    val helper = runConfig.context?.getHelper(project) as? CargoBuildConfigurationHelper ?: return null
    val buildTargetData = runConfig.targetAndConfigurationData ?: return null
    if (!BuildTargetAndConfigurationData.checkData(helper, buildTargetData, problems, false, true)) return null

    val buildTarget = helper.findTarget(buildTargetData.target)
    if (buildTarget == null) {
        problems.problems.add(
            CidrRunnerBundle.message(
                "build.configuration.parameterNotSelected",
                CidrRunnerBundle.message("build.configuration.target")
            )
        )
        return null
    }

    val configuration = if (buildTargetData.configurationName == null) {
        helper.getDefaultConfiguration(buildTarget)
    } else {
        helper.findConfiguration(buildTarget, buildTargetData.configurationName)
    }

    return if (problems.hasProblems()) null else configuration
}
