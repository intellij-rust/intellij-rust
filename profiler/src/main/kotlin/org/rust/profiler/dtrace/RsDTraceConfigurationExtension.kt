/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler.dtrace

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.UnixProcessManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.profiler.*
import com.intellij.profiler.clion.DTraceProfilerConfigurable
import com.intellij.profiler.clion.NativeTargetProcess
import com.intellij.profiler.clion.dtrace.DTraceProfilerSettings
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.profiler.RsProfilerRunner
import org.rust.profiler.legacy.RsProfilerRunnerLegacy
import java.io.File

class RsDTraceConfigurationExtension : CargoCommandConfigurationExtension() {
    override fun isApplicableFor(configuration: CargoCommandConfiguration): Boolean = true

    override fun isEnabledFor(
        applicableConfiguration: CargoCommandConfiguration,
        runnerSettings: RunnerSettings?
    ): Boolean = isEnabledFor()

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        validateDTraceSettings(configuration.project)
        val starterPath = profilerStarterPath()
        if (!starterPath.exists()) throw ExecutionException("Internal error: Can't find process starter")
        cmdLine.withEnvironment(DYLD_INSERT_LIBRARIES, starterPath.absolutePath)
    }

    override fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return

        val targetProcess = (handler as? BaseProcessHandler<*>)?.process
            ?: throw ExecutionException("Profiler connection error: can't detect target process id")
        val namedProcess = NativeTargetProcess(OSProcessUtil.getProcessID(targetProcess), configuration.name)
        val project = configuration.project
        @Suppress("UnstableApiUsage")
        RsDTraceProfilerProcess.attach(namedProcess, PerformInBackgroundOption.ALWAYS_BACKGROUND, 10000, project)
            .installErrorHandlers(project)
            .onError { ExecutionManagerImpl.stopProcess(handler) }
            .onSuccess { process ->
                UnixProcessManager.sendSigIntToProcessTree(targetProcess) //wakeup starter and finally run targetProcess code
                ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(process)
            }
    }

    companion object {
        private val PROFILER_RUNNER_IDS = listOf(RsProfilerRunner.RUNNER_ID, RsProfilerRunnerLegacy.RUNNER_ID)
        private const val DYLD_INSERT_LIBRARIES = "DYLD_INSERT_LIBRARIES"

        private fun profilerStarterPath(): File = File(PathManager.getBinPath(), "profiler/mac/libosx-starter.dylib")

        @Throws(MisConfiguredException::class)
        private fun validateDTraceSettings(project: Project) {
            val state = DTraceProfilerSettings.instance.state
            throw validateLocalPath(state.executablePath.orEmpty(), "DTrace executable", project, DTraceProfilerConfigurable::class.java)
                ?: validateFrequency(state.samplingFrequency, project, DTraceProfilerConfigurable::class.java)
                ?: return
        }

        fun isEnabledFor(): Boolean = SystemInfo.isMac
    }
}
