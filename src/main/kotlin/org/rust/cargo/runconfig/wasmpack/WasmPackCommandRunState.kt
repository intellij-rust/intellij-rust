/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.psi.search.GlobalSearchScopes
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.console.CargoConsoleBuilder
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.WasmPack
import java.io.File

class WasmPackCommandRunState(
    environment: ExecutionEnvironment,
    runConfiguration: WasmPackCommandConfiguration,
    wasmPack: WasmPack,
    workingDirectory: File
): WasmPackCommandRunStateBase(environment, runConfiguration, wasmPack, workingDirectory) {
    init {
        val scope = GlobalSearchScopes.executionScope(environment.project, environment.runProfile)
        consoleBuilder = CargoConsoleBuilder(environment.project, scope)
    }
}
