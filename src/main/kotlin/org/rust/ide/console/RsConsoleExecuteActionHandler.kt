/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.ProcessHandler
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.openapiext.showErrorHint

class RsConsoleExecuteActionHandler(
    processHandler: ProcessHandler,
    private val consoleCommunication: RsConsoleCommunication
) : ProcessBackedConsoleExecuteActionHandler(processHandler, false) {

    var isEnabled: Boolean = false

    override fun processLine(line: String) {
        val lineEscaped = line.replace("\n", "\u2028")
        super.processLine(lineEscaped)
    }

    override fun runExecuteAction(console: LanguageConsoleView) {
        if (!isEnabled) {
            console.consoleEditor.showErrorHint(consoleIsNotEnabledMessage)
            return
        }

        if (!canExecuteNow()) {
            console.consoleEditor.showErrorHint(prevCommandRunningMessage)
            return
        }

        consoleCommunication.onExecutionBegin()
        copyToHistoryAndExecute(console)
    }

    private fun canExecuteNow(): Boolean = !consoleCommunication.isExecuting

    private fun copyToHistoryAndExecute(console: LanguageConsoleView) = super.runExecuteAction(console)

    companion object {
        @Nls
        val prevCommandRunningMessage: String =
            RsBundle.message("previous.command.is.still.running.please.wait.or.press.ctrl.c.in.console.to.interrupt")
        @Nls
        val consoleIsNotEnabledMessage: String = RsBundle.message("console.is.not.enabled")
    }
}
