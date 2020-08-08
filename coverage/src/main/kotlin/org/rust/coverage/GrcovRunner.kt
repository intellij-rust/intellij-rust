/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.coverage.CoverageExecutor
import com.intellij.coverage.CoverageHelper
import com.intellij.coverage.CoverageRunnerData
import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoConstants.ProjectLayout
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsDefaultProgramRunnerBase
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.buildtool.cargoPatches
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo.Companion.checkNeedInstallGrcov
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath
import java.io.File

class GrcovRunner : RsDefaultProgramRunnerBase() {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != CoverageExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return !isBuildConfiguration(profile) && getBuildConfiguration(profile) != null
    }

    override fun createConfigurationData(settingsProvider: ConfigurationInfoProvider): RunnerSettings {
        return CoverageRunnerData()
    }

    override fun execute(environment: ExecutionEnvironment) {
        val project = environment.project
        if (checkNeedInstallGrcov(project)) return
        val state = environment.state as CargoRunStateBase
        if (!checkIsNightlyToolchain(project, state.config)) return
        val workingDirectory = state.commandLine.workingDirectory.toFile()
        cleanOldCoverageData(workingDirectory)
        environment.cargoPatches += cargoCoveragePatch
        super.execute(environment)
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val workingDirectory = (state as CargoRunStateBase).commandLine.workingDirectory.toFile()
        val descriptor = super.doExecute(state, environment)
        descriptor?.processHandler?.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                startCollectingCoverage(workingDirectory, environment)
            }
        })
        return descriptor
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(GrcovRunner::class.java)

        const val RUNNER_ID: String = "GrcovRunner"

        private val cargoCoveragePatch: CargoPatch = { commandLine ->
            val oldVariables = commandLine.environmentVariables
            val environmentVariables = EnvironmentVariablesData.create(
                oldVariables.envs + mapOf(
                    "CARGO_INCREMENTAL" to "0",
                    "RUSTFLAGS" to "-Zprofile -Ccodegen-units=1 -Copt-level=0 -Clink-dead-code -Coverflow-checks=off -Zpanic_abort_tests -Cpanic=abort",
                    "RUSTDOCFLAGS" to "-Cpanic=abort"
                ),
                oldVariables.isPassParentEnvs
            )
            commandLine.copy(environmentVariables = environmentVariables)
        }

        private fun cleanOldCoverageData(workingDirectory: File) {
            val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(workingDirectory) ?: return
            val targetDir = root.findChild(ProjectLayout.target) ?: return

            val toDelete = mutableListOf<VirtualFile>()
            VfsUtil.iterateChildrenRecursively(targetDir, null) { fileOrDir ->
                if (!fileOrDir.isDirectory && fileOrDir.extension == "gcda") {
                    toDelete.add(fileOrDir)
                }
                true
            }

            if (toDelete.isEmpty()) return
            WriteAction.runAndWait<Throwable> { toDelete.forEach { it.delete(null) } }
        }

        private fun startCollectingCoverage(workingDirectory: File, environment: ExecutionEnvironment) {
            val project = environment.project
            val runConfiguration = environment.runProfile as? RunConfigurationBase<*> ?: return
            val runnerSettings = environment.runnerSettings ?: return
            val grcov = project.toolchain?.grcov() ?: return

            val coverageEnabledConfiguration = CoverageEnabledConfiguration.getOrCreate(runConfiguration)
                as? RsCoverageEnabledConfiguration ?: return
            val coverageFilePath = coverageEnabledConfiguration.coverageFilePath?.toPath() ?: return
            val coverageCmd = grcov.createCommandLine(workingDirectory, coverageFilePath)

            try {
                val coverageProcess = OSProcessHandler(coverageCmd)
                coverageEnabledConfiguration.coverageProcess = coverageProcess
                CoverageHelper.attachToProcess(runConfiguration, coverageProcess, runnerSettings)
                coverageProcess.addProcessListener(object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        LOG.debug(event.text)
                    }
                })
                coverageProcess.startNotify()
            } catch (e: ExecutionException) {
                LOG.error(e)
            }
        }

        private fun checkIsNightlyToolchain(project: Project, config: CargoCommandConfiguration.CleanConfiguration.Ok): Boolean {
            var channel: RustChannel? = config.cmd.channel
            if (channel == RustChannel.DEFAULT) {
                channel = project.computeWithCancelableProgress("Fetching rustc version...") {
                    config.toolchain.queryVersions().rustc?.channel
                }
            }
            if (channel == RustChannel.NIGHTLY) return true

            val option = Messages.showDialog(
                project,
                "Code coverage is available only with nightly toolchain",
                "Unable to run with coverage",
                arrayOf("Configure"),
                Messages.OK,
                Messages.getErrorIcon()
            )
            if (option == Messages.OK) {
                EditConfigurationsDialog(project).show()
            }

            return false
        }
    }
}
