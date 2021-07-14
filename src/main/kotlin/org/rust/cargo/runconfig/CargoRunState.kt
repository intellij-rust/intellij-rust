/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.psi.search.GlobalSearchScopes
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.console.CargoConsoleBuilder
import org.rust.cargo.runconfig.target.CargoRunStateTargetAware

class CargoRunState(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunStateTargetAware(environment, runConfiguration, config) {
    init {
        val scope = GlobalSearchScopes.executionScope(project, environment.runProfile)
        consoleBuilder = CargoConsoleBuilder(project, scope)
        createFilters(cargoProject).forEach { consoleBuilder.addFilter(it) }
    }
}
