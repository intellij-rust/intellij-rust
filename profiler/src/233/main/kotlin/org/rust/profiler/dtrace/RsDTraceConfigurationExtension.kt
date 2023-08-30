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
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.profiler.ProfilerToolWindowManager
import com.intellij.profiler.clion.DTraceProfilerConfigurationExtension
import com.intellij.profiler.clion.NativeTargetProcess
import com.intellij.profiler.clion.ProfilerConfigurationExtension
import com.intellij.profiler.clion.ProfilerEnvironmentHost
import com.intellij.profiler.dtrace.legacyDTraceProfilerConfiguration
import com.intellij.profiler.installErrorHandlers
import com.intellij.profiler.statistics.ProfilerUsageTriggerCollector
import org.rust.RsBundle
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.profiler.RsProfilerRunner
import org.rust.profiler.RsProfilerRunner.Companion.IJ_RUNNER_ID
import org.rust.profiler.legacy.RsProfilerRunnerLegacy

class RsDTraceConfigurationExtension : CargoCommandConfigurationExtension() {
    private val delegate: ProfilerConfigurationExtension = DTraceProfilerConfigurationExtension()

    override fun isApplicableFor(configuration: CargoCommandConfiguration): Boolean =
        delegate.isApplicableFor(configuration)

    override fun isEnabledFor(
        applicableConfiguration: CargoCommandConfiguration,
        runnerSettings: RunnerSettings?
    ): Boolean = delegate.isEnabledFor(applicableConfiguration, runnerSettings)

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        delegate.patchCommandLine(configuration, environment.runnerSettings, cmdLine, IJ_RUNNER_ID, context)
    }

    override fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        val project = configuration.project
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        if (ProfilerEnvironmentHost.isRemote(project)) return
        val targetProcess = (handler as? BaseProcessHandler<*>)?.process
            ?: throw ExecutionException(RsBundle.message("dialog.message.profiler.connection.error.can.t.detect.target.process.id"))
        ProfilerUsageTriggerCollector.logRecordingStarted(project, legacyDTraceProfilerConfiguration.configurationTypeId, configuration.type.id)
        val namedProcess = NativeTargetProcess(OSProcessUtil.getProcessID(targetProcess), configuration.name)
        RsDTraceProfilerProcess.attach(namedProcess, PerformInBackgroundOption.ALWAYS_BACKGROUND, 10000, project)
            .installErrorHandlers(project)
            .onError { ExecutionManagerImpl.stopProcess(handler) }
            .onSuccess { process ->
                UnixProcessManager.sendSigIntToProcessTree(targetProcess) //wakeup starter and finally run targetProcess code
                ProfilerToolWindowManager.getInstance(project).addProfilerProcessTab(process)
            }
    }

    companion object {
        private val PROFILER_RUNNER_IDS: List<String> = listOf(RsProfilerRunner.RUNNER_ID, RsProfilerRunnerLegacy.RUNNER_ID)
    }
}
