/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.test

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestProxyRoot
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import org.rust.stdext.removeLast
import org.rustSlowTests.cargo.runconfig.RunConfigurationTestBase

abstract class CargoTestRunnerTestBase : RunConfigurationTestBase() {

    protected fun executeAndGetTestRoot(configuration: RunConfiguration): SMTestProxy.SMRootTestProxy {
        val result = execute(configuration)
        val executionConsole = result.executionConsole as SMTRunnerConsoleView
        val testsRootNode = executionConsole.resultsViewer.testsRootNode
        with(result.processHandler) {
            startNotify()
            waitFor()
        }
        UIUtil.dispatchAllInvocationEvents()
        Disposer.register(project, executionConsole)
        return testsRootNode
    }

    protected fun SMTestProxy.SMRootTestProxy.findTestByName(testFullName: String): SMTestProxy {
        val fullNameBuffer = mutableListOf<String>()

        fun find(test: SMTestProxy): SMTestProxy? {
            if (test !is TestProxyRoot) {
                fullNameBuffer.add(test.name)
            }
            if (testFullName == fullNameBuffer.joinToString("::")) return test
            for (child in test.children) {
                val result = find(child)
                if (result != null) return result
            }
            if (test !is TestProxyRoot) {
                fullNameBuffer.removeLast()
            }
            return null
        }

        return checkNotNull(find(this)) { "Could not find the test" }
    }
}
