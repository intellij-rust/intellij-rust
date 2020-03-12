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
        /**
         * \u0091 and \u0092 are C1 control codes (https://en.wikipedia.org/wiki/C0_and_C1_control_codes#C1_control_codes_for_general_use)
         * with names "Private Use 1" and "Private Use 2"
         * and meaning
         *     "Reserved for a function without standardized meaning for private use as required,
         *      subject to the prior agreement of the sender and the recipient of the data."
         * so they are ideal for our purpose
         */
        // BACKCOMPAT: Evcxr 0.4.6. Remove \u0001 and \u0002
        val SUCCESS_EXECUTION_MARKER: Regex = Regex("[\u0091\u0001]")
        val FAILED_EXECUTION_MARKER: Regex = Regex("[\u0092\u0002]")
    }
}
