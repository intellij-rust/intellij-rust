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
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.DefaultProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoConstants.ProjectLayout
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.buildtool.cargoPatches
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo.Companion.checkNeedInstallGrcov
import org.rust.cargo.toolchain.RustChannel
import org.rust.stdext.toPath
import java.io.File

class GrcovRunner : DefaultProgramRunner() {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != CoverageExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return !isBuildConfiguration(profile) && getBuildConfiguration(profile) != null
    }

    override fun createConfigurationData(settingsProvider: ConfigurationInfoProvider): RunnerSettings {
        return CoverageRunnerData()
    }

    override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
        if (checkNeedInstallGrcov(environment.project)) return

        (state as CargoRunStateBase).addCommandLinePatch(cargoCoveragePatch)
        environment.cargoPatches.add(cargoCoveragePatch)

        val workingDirectory = state.commandLine.workingDirectory.toFile()
        cleanOldCoverageData(workingDirectory)

        super.execute(environment, callback, state)
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
                    "RUSTFLAGS" to "-Zprofile -Ccodegen-units=1 -Cinline-threshold=0 -Clink-dead-code -Zno-landing-pads"
                ),
                oldVariables.isPassParentEnvs
            )
            commandLine.copy(channel = RustChannel.NIGHTLY, environmentVariables = environmentVariables)
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
    }
}
