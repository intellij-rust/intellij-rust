/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler.perf

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.profiler.ProfilerToolWindowManager
import com.intellij.profiler.clion.perf.PerfProfilerSettings
import com.intellij.profiler.clion.perf.PerfUtils
import com.intellij.profiler.statistics.ProfilerUsageTriggerCollector
import com.intellij.util.text.nullize
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import org.rust.RsBundle
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.profiler.RsProfilerRunner
import org.rust.profiler.legacy.RsProfilerRunnerLegacy
import java.nio.file.Path

class RsPerfConfigurationExtension : CargoCommandConfigurationExtension() {

    override fun isApplicableFor(configuration: CargoCommandConfiguration): Boolean = true

    override fun isEnabledFor(
        applicableConfiguration: CargoCommandConfiguration,
        runnerSettings: RunnerSettings?
    ): Boolean = isEnabledFor(applicableConfiguration)

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        val toolEnvironment = toolEnvironment ?: return
        val toolchain = configuration.clean().ok?.toolchain ?: return

        if (isUnsupportedWSL(toolEnvironment)) {
            throw ExecutionException(RsBundle.message("dialog.message.perf.profiler.not.available.for.selected.wsl.distribution.try.updating.wsl.to.newer.one"))
        }
        if (isUnsupportedToolchain(toolEnvironment)) {
            throw ExecutionException(RsBundle.message("dialog.message.perf.profiler.not.available.for.selected.toolchain"))
        }

        val project = configuration.project
        PerfUtils.validatePerfSettings(toolEnvironment, project)?.let { throw it }
        PerfUtils.validateKernelVariables(toolEnvironment, project)?.let { throw it }

        val settings = PerfProfilerSettings.instance.state
        val perfPath = toolchain.toLocalPath(settings.executablePath.orEmpty())
        val outputFilePath = PerfUtils.createOutputFilePath(toolEnvironment, settings.outputDirectory.nullize())
        cmdLine.addPerfStarter(perfPath, settings.samplingFrequency, settings.defaultCmdArgs, outputFilePath.toString())
        toolchain.patchCommandLine(cmdLine)
        context.putUserData(PERF_OUTPUT_FILE_KEY, outputFilePath)
    }

    override fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        val toolEnvironment = toolEnvironment ?: return

        if (isUnsupportedWSL(toolEnvironment) || isUnsupportedToolchain(toolEnvironment)) return
        if (handler !is BaseProcessHandler<*>)
            throw ExecutionException(RsBundle.message("dialog.message.can.t.detect.target.process.id"))
        //since we executing `perf record` instead of original app, we should kill it softly to be able finish correctly
        if (handler is KillableProcessHandler) {
            handler.setShouldKillProcessSoftly(true)
        }
        val outputFile = context.getUserData(PERF_OUTPUT_FILE_KEY)
            ?: throw ExecutionException(RsBundle.message("dialog.message.can.t.get.output.perf.data.file"))

        val project = configuration.project
        val profilerProcess = createProfilerProcess(
            handler,
            outputFile,
            configuration.name,
            project,
            System.currentTimeMillis(),
            toolEnvironment
        )
        ProfilerUsageTriggerCollector.logRecordingStarted(project, profilerProcess.profilerConfiguration.configurationTypeId, configuration.type.id)
        ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(profilerProcess)
    }

    companion object {
        private val PERF_OUTPUT_FILE_KEY = Key.create<Path>("perf.output")

        private val PROFILER_RUNNER_IDS = listOf(RsProfilerRunner.RUNNER_ID, RsProfilerRunnerLegacy.RUNNER_ID)

        private val toolEnvironment: CidrToolEnvironment?
            get() = CPPToolchains.getInstance().defaultToolchain?.let { CPPEnvironment(it) }

        private fun isUnsupportedWSL(environment: CidrToolEnvironment): Boolean {
            return PerfUtils.isWSL(environment) && PerfUtils.getWSLVersion(environment) < 2
        }

        private fun isUnsupportedToolchain(environment: CidrToolEnvironment): Boolean {
            return SystemInfo.isWindows && !environment.hostMachine.isRemote
        }

        private fun GeneralCommandLine.addPerfStarter(
            perfPath: String,
            samplingFrequency: Int,
            defaultArgs: List<String>,
            outputPath: String
        ): GeneralCommandLine = this.apply {
            parametersList.prependAll("record", "--freq=$samplingFrequency", *defaultArgs.toTypedArray(), "-o", outputPath, exePath)
            exePath = perfPath
        }

        fun isEnabledFor(configuration: CargoCommandConfiguration): Boolean {
            val toolchain = configuration.clean().ok?.toolchain
            return SystemInfo.isLinux || SystemInfo.isWindows && toolchain is RsWslToolchain
        }
    }
}
