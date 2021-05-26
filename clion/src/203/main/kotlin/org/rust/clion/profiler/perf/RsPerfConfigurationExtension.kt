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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.profiler.*
import com.intellij.profiler.clion.CPPProfilerSettings
import com.intellij.profiler.clion.perf.PerfProfilerProcess
import com.intellij.profiler.clion.perf.PerfUtils
import com.intellij.profiler.clion.profilerConfigurable
import com.intellij.profiler.linux.HasInvalidVariables
import com.intellij.profiler.linux.KernelVariable
import com.intellij.profiler.linux.KernelVariablesChangeRequiredException
import com.intellij.profiler.linux.checkKernelVariables
import com.intellij.profiler.statistics.ProfilerUsageTriggerCollector
import com.intellij.util.text.nullize
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.lang.core.psi.RsFunction
import java.io.File
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
        val settings = CPPProfilerSettings.instance.state
        val perfPath = settings.executablePath.orEmpty()
        validatePerfSettings(project)
        val validationResult = checkKernelVariables(REQUIRED_KERNEL_VARIABLES)
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
        val profilerProcess = PerfProfilerProcess(
            handler,
            outputFile,
            configuration.name,
            project,
            System.currentTimeMillis(),
            RsFunction::class.java
        )
        ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(profilerProcess)
    }

    @Throws(MisConfiguredException::class)
    private fun validatePerfSettings(project: Project) {
        val state = CPPProfilerSettings.instance.state
        throw validateLocalPath(state.executablePath.orEmpty(), "Perf executable", project, profilerConfigurable::class.java)
            ?: validateFrequency(state.samplingFrequency, project, profilerConfigurable::class.java)
            ?: validateOutputDirectory(state.outputDirectory.orEmpty(), project, profilerConfigurable::class.java)
            ?: return
    }

    companion object {
        private val REQUIRED_KERNEL_VARIABLES = listOf(
            KernelVariable("perf_event_paranoid", "1") { it.toInt() <= 1 }, //required, error otherwise
            KernelVariable("kptr_restrict", "0") { it == "0" } //useful, warning otherwise
        )
        private val PERF_OUTPUT_FILE_KEY = Key.create<File>("perf.output")
    }
}
