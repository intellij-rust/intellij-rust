/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.util.SystemInfo
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.clion.profiler.RsProfilerRunner
import org.rust.clion.profiler.legacy.RsProfilerRunnerLegacy

abstract class RsPerfConfigurationExtensionBase : CargoCommandConfigurationExtension() {

    override fun isApplicableFor(configuration: CargoCommandConfiguration): Boolean = true

    override fun isEnabledFor(applicableConfiguration: CargoCommandConfiguration, runnerSettings: RunnerSettings?): Boolean =
        SystemInfo.isLinux

    companion object {
        val PROFILER_RUNNER_IDS = listOf(RsProfilerRunner.RUNNER_ID, RsProfilerRunnerLegacy.RUNNER_ID)

        fun GeneralCommandLine.addPerfStarter(
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
