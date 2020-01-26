/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class RsConsoleCommunication(private val consoleView: RsConsoleView) {

    var isExecuting: Boolean = false
        private set
    private var receivedInitialPrompt: Boolean = false
    private var lastCommandContext: RsConsoleOneCommandContext? = null

    fun onExecutionBegin() {
        check(!isExecuting) { "new command must not be executed before previous command finishes" }
        isExecuting = true

        val codeFragment = consoleView.codeFragment ?: return
        if (codeFragment.text.isEmpty()) return
        lastCommandContext = RsConsoleOneCommandContext(codeFragment)
    }

    private fun onExecutionEnd(success: Boolean) {
        if (!receivedInitialPrompt) {
            receivedInitialPrompt = true
            return
        }

        val lastCommandContext = lastCommandContext
        if (success && lastCommandContext != null) {
            consoleView.addToContext(lastCommandContext)
        }
        this.lastCommandContext = null

        check(isExecuting)
        isExecuting = false
    }

    fun processText(textOriginal: String): String {
        val text = textOriginal.replace("\r", "")
        return when {
            text.contains(SUCCESS_EXECUTION_MARKER) -> {
                onExecutionEnd(true)
                text.replace(SUCCESS_EXECUTION_MARKER, "")
            }
            text.contains(FAILED_EXECUTION_MARKER) -> {
                onExecutionEnd(false)
                text.replace(FAILED_EXECUTION_MARKER, "")
            }
            text == RsConsoleView.PROMPT -> ""
            else -> text
        }
    }

    companion object {
        const val SUCCESS_EXECUTION_MARKER: String = "\u0001"
        const val FAILED_EXECUTION_MARKER: String = "\u0002"
    }
}
