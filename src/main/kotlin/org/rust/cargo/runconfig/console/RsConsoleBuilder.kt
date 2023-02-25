/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.console

import com.intellij.execution.Executor
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.terminal.TerminalExecutionConsole
import org.rust.cargo.runconfig.RsCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.hasRemoteTarget
import org.rust.cargo.runconfig.test.CargoTestConsoleProperties.Companion.TEST_FRAMEWORK_NAME

open class RsConsoleBuilder(
    project: Project,
    val config: RsCommandConfiguration
) : TextConsoleBuilderImpl(project, ExecutionSearchScopes.executionScope(project, config)) {
    override fun createConsole(): ConsoleView =
        if (config.emulateTerminal && !config.hasRemoteTarget) {
            TerminalExecutionConsole(project, null)
        } else {
            CargoConsoleView(project, scope, isViewer, true)
        }
}

class CargoTestConsoleBuilder(
    private val config: CargoCommandConfiguration,
    private val executor: Executor
) : TextConsoleBuilder() {
    private val filters: MutableList<Filter> = mutableListOf()

    override fun addFilter(filter: Filter) {
        filters.add(filter)
    }

    override fun setViewer(isViewer: Boolean) {}

    override fun getConsole(): ConsoleView {
        val consoleProperties = config.createTestConsoleProperties(executor)
        val consoleView = SMTestRunnerConnectionUtil.createConsole(TEST_FRAMEWORK_NAME, consoleProperties!!)
        filters.forEach { consoleView.addMessageFilter(it) }
        return consoleView
    }
}
