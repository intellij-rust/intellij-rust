/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.cargo

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.CLionLauncher
import com.jetbrains.cidr.cpp.execution.compound.CidrCompoundConfigurationContextBase
import com.jetbrains.cidr.cpp.execution.compound.CidrCompoundRunConfiguration
import com.jetbrains.cidr.cpp.execution.compound.ConfigurationHelperType
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.execution.ExecutableData
import com.jetbrains.cidr.toolchains.EnvironmentProblems
import org.rust.cargo.runconfig.RsExecutableRunner.Companion.ARTIFACTS
import org.rust.cargo.runconfig.RsExecutableRunner.Companion.artifacts
import org.rust.cargo.runconfig.buildtool.CargoBuildTaskProvider
import java.io.File
import java.util.concurrent.CompletableFuture

class CargoCompoundConfigurationContext : CidrCompoundConfigurationContextBase(CargoBuildTarget::class.java) {
    override val id: String = ID

    @Throws(ExecutionException::class)
    override fun getRunFileAndEnvironment(launcher: CLionLauncher): Pair<File?, CPPEnvironment?> =
        getRunFile(launcher) to getRunEnvironment(launcher)

    override fun executeBuildTask(
        context: DataContext,
        configuration: RunConfiguration,
        env: ExecutionEnvironment,
        task: BeforeRunTask<*>
    ): Boolean {
        if (configuration !is CidrCompoundRunConfiguration) return false
        val buildConfiguration = getCidrBuildConfiguration(env.project, configuration) ?: return false
        env.putUserData(ARTIFACTS, CompletableFuture())
        val result = CargoBuildTaskProvider().executeTask(context, buildConfiguration.configuration, env, CargoBuildTaskProvider.BuildTask())
        val artifacts = env.artifacts.orEmpty()
        val artifact = artifacts.firstOrNull()
        val binary = artifact?.executables?.firstOrNull()
        if (binary != null) {
            configuration.executableData = ExecutableData(binary)
        }
        return result
    }

    override fun getHelper(project: Project): ConfigurationHelperType = CargoBuildConfigurationHelper(project)

    companion object {
        private const val ID: String = "CargoCompoundConfigurationContext"

        private fun getRunFile(launcher: CLionLauncher): File? {
            val configuration = launcher.configuration as? CidrCompoundRunConfiguration
            val path = configuration?.executableData?.path
            return path?.let { File(it) }
        }

        @Throws(ExecutionException::class)
        private fun getRunEnvironment(launcher: CLionLauncher): CPPEnvironment {
            val environmentProblems = EnvironmentProblems()
            val projectBaseDir = launcher.project.basePath?.let { File(it) }
            val environment = CPPToolchains.createCPPEnvironment(launcher.project, projectBaseDir, null, environmentProblems, false, null)
            if (environment == null) {
                environmentProblems.throwAsExecutionException()
            }
            return environment!!
        }
    }
}
