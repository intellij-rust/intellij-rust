/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.SearchScopeProvider
import com.intellij.execution.runners.ExecutionEnvironment
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.console.CargoConsoleBuilder

class CargoRunState(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunStateBase(environment, runConfiguration, config) {
    init {
        val scope = SearchScopeProvider.createSearchScope(environment.project, environment.runProfile)
        consoleBuilder = CargoConsoleBuilder(environment.project, scope)
        createFilters().forEach { consoleBuilder.addFilter(it) }
    }
}
