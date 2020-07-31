/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import org.rust.cargo.toolchain.WasmPackCommandLine

fun RunManager.createWasmPackCommandRunConfiguration(wasmPackCommandLine: WasmPackCommandLine, name: String? = null): RunnerAndConfigurationSettings {
    val runnerAndConfigurationSettings = createConfiguration(name ?: wasmPackCommandLine.command,
        WasmPackCommandConfigurationType.getInstance().factory)
    val configuration = runnerAndConfigurationSettings.configuration as WasmPackCommandConfiguration
    configuration.setFromCmd(wasmPackCommandLine)
    return runnerAndConfigurationSettings
}
