/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.ProcessHandler

class RsConsoleExecuteActionHandler(
    processHandler: ProcessHandler,
    private val consoleRunner: RsConsoleRunner,
    private val consoleCommunication: RsConsoleCommunication,
    private val consoleView: RsConsoleView
) : ProcessBackedConsoleExecuteActionHandler(processHandler, false) {

    var isEnabled: Boolean = false

    override fun processLine(line: String) {
        val entry = CommandHistory.Entry(line)
        consoleRunner.commandHistory.addEntry(entry)
        consoleView.updateCodeFragmentContext()
        super.processLine(line)
    }

    override fun runExecuteAction(console: LanguageConsoleView) {
        if (!isEnabled) {
            HintManager.getInstance().showErrorHint(console.consoleEditor, consoleIsNotEnabledMessage)
            return
        }

        if (!canExecuteNow()) {
            HintManager.getInstance().showErrorHint(console.consoleEditor, prevCommandRunningMessage)
            return
        }

        consoleCommunication.onExecutionBegin()
        copyToHistoryAndExecute(console)
    }

    private fun canExecuteNow(): Boolean = !consoleCommunication.isExecuting

    private fun copyToHistoryAndExecute(console: LanguageConsoleView) = super.runExecuteAction(console)

    companion object {
        const val prevCommandRunningMessage: String =
            "Previous command is still running. Please wait or press Ctrl+C in console to interrupt."
        const val consoleIsNotEnabledMessage: String = "Console is not enabled."
    }
}
