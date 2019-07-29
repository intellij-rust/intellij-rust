/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.profiler.ProfilerToolWindowManager
import com.intellij.profiler.clion.CPPProfilerSettings
import com.intellij.profiler.clion.perf.Addr2LineProcess
import com.intellij.profiler.clion.perf.AddressSymbolizer
import com.intellij.profiler.keepTempProfilerFiles
import com.intellij.profiler.linux.HasInvalidVariables
import com.intellij.profiler.linux.KernelVariable
import com.intellij.profiler.linux.KernelVariablesChangeRequiredException
import com.intellij.profiler.linux.checkKernelVariables
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.clion.profiler.RsProfilerRunner
import org.rust.clion.profiler.legacy.RsProfilerRunnerLegacy
import java.io.File

class RsPerfConfigurationExtension : CargoCommandConfigurationExtension() {
    private val PERF_OUTPUT_FILE_KEY = Key.create<File>("perf.output")

    override fun isApplicableFor(configuration: CargoCommandConfiguration): Boolean = true

    override fun isEnabledFor(applicableConfiguration: CargoCommandConfiguration, runnerSettings: RunnerSettings?): Boolean =
        SystemInfo.isLinux

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        val project = configuration.project
        val settings = CPPProfilerSettings.instance.state
        val perfPath = settings.executablePath.orEmpty()
        val validationResult = checkKernelVariables(requiredKernelVariables)
        if (validationResult is HasInvalidVariables) {
            throw KernelVariablesChangeRequiredException(validationResult, project)
        }
        val outputFile = FileUtil.createTempFile("perf", null, !keepTempProfilerFiles())
        cmdLine.addPerfStarter(perfPath, settings.samplingFrequency, settings.defaultCmdArgs, outputFile.absolutePath)
        context.putUserData(PERF_OUTPUT_FILE_KEY, outputFile)
    }

    override fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        if (handler !is BaseProcessHandler<*>)
            throw ExecutionException("Can't detect target process id")
        val outputFile = context.getUserData(PERF_OUTPUT_FILE_KEY)
            ?: throw ExecutionException("Can't get output perf data file")

        val project = configuration.project
        val profilerProcess = RsPerfProfilerProcess(
            handler,
            outputFile.path,
            createAddressSymbolizer(),
            configuration.name,
            project,
            System.currentTimeMillis()
        )
        ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(profilerProcess)
    }

    private fun createAddressSymbolizer(): AddressSymbolizer? {
        return if (Addr2LineProcess.isAvailable) {
            Addr2LineProcess()
        } else {
            null
        }
    }

    companion object {
        private val PROFILER_RUNNER_IDS = listOf(RsProfilerRunner.RUNNER_ID, RsProfilerRunnerLegacy.RUNNER_ID)

        private val requiredKernelVariables = listOf(
            KernelVariable("perf_event_paranoid", "1") { it.toInt() <= 1 }, //required, error otherwise
            KernelVariable("kptr_restrict", "0") { it == "0" } //useful, warning otherwise
        )

        private fun GeneralCommandLine.addPerfStarter(
            perfPath: String,
            samplingFrequency: Int,
            defaultArgs: List<String>,
            outputPath: String
        ): GeneralCommandLine = this.apply {
            parametersList.prependAll("record", "--freq=$samplingFrequency", *defaultArgs.toTypedArray(), "-o", outputPath, exePath)
            exePath = perfPath
        }
    }
}
