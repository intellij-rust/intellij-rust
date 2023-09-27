/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler.perf

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.profiler.clion.ProfilerConfigurationExtension
import com.intellij.profiler.clion.perf.PerfProfilerConfigurationExtension
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.profiler.RsProfilerRunner
import org.rust.profiler.RsProfilerRunner.Companion.IJ_RUNNER_ID
import org.rust.profiler.legacy.RsProfilerRunnerLegacy

class RsPerfConfigurationExtension : CargoCommandConfigurationExtension() {
    private val delegate: ProfilerConfigurationExtension = PerfProfilerConfigurationExtension()

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
        val toolchain = configuration.clean().ok?.toolchain ?: return
        toolchain.patchCommandLine(cmdLine, withSudo = false)
    }

    override fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in PROFILER_RUNNER_IDS) return
        delegate.attachToProcess(configuration, handler, environment.runnerSettings, IJ_RUNNER_ID, context)
    }

    companion object {
        private val PROFILER_RUNNER_IDS = listOf(RsProfilerRunner.RUNNER_ID, RsProfilerRunnerLegacy.RUNNER_ID)
    }
}
