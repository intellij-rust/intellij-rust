/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class RsConsoleCommunication {

    var isExecuting: Boolean = false
        private set

    fun onExecutionBegin() {
        isExecuting = true
    }

    fun processText(textOriginal: String): String {
        val text = textOriginal.replace("\r", "")
        if (text == RsConsoleView.PROMPT) {
            isExecuting = false
            return ""
        }
        return text
    }
}
