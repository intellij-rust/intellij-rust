/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.psi.search.GlobalSearchScopes
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.console.CargoConsoleBuilder

class CargoRunState(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunStateBase(environment, runConfiguration, config) {
    init {
        val scope = GlobalSearchScopes.executionScope(environment.project, environment.runProfile)
        consoleBuilder = CargoConsoleBuilder(environment.project, scope)
        createFilters().forEach { consoleBuilder.addFilter(it) }
    }
}
