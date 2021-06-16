/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key
import com.intellij.profiler.ProfilerToolWindowManager
import com.intellij.profiler.clion.perf.PerfProfilerProcess
import com.intellij.profiler.clion.perf.PerfUtils
import com.intellij.profiler.statistics.ProfilerUsageTriggerCollector
import com.intellij.util.text.nullize
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.lang.core.psi.RsFunction
import java.nio.file.Path

class RsPerfConfigurationExtension : RsPerfConfigurationExtensionBase() {

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        val project = configuration.project
        val settings = getPerfSettings()
        val perfPath = settings.executablePath.orEmpty()
        PerfUtils.validatePerfSettings(TOOL_ENVIRONMENT, project)?.let { throw it }
        PerfUtils.validateKernelVariables(TOOL_ENVIRONMENT, project)?.let { throw it }
        val outputFilePath = PerfUtils.createOutputFilePath(TOOL_ENVIRONMENT, settings.outputDirectory.nullize())
        cmdLine.addPerfStarter(perfPath, settings.samplingFrequency, settings.defaultCmdArgs, outputFilePath.toString())
        context.putUserData(PERF_OUTPUT_FILE_KEY, outputFilePath)
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
        //since we executing `perf record` instead of original app, we should kill it softly to be able finish correctly
        if (handler is KillableProcessHandler) {
            handler.setShouldKillProcessSoftly(true)
        }
        val outputFile = context.getUserData(PERF_OUTPUT_FILE_KEY)
            ?: throw ExecutionException("Can't get output perf data file")

        val project = configuration.project
        val profilerProcess = PerfProfilerProcess(
            handler,
            false,
            outputFile,
            configuration.name,
            project,
            System.currentTimeMillis(),
            TOOL_ENVIRONMENT,
            RsFunction::class.java
        )
        ProfilerUsageTriggerCollector.reportStart(project, profilerProcess.profilerConfiguration.configurationTypeId, configuration.type.id)
        ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(profilerProcess)
    }

    companion object {
        private val PERF_OUTPUT_FILE_KEY = Key.create<Path>("perf.output")

        // TODO: works only for local profiling
        private val TOOL_ENVIRONMENT = CidrToolEnvironment()
    }
}
