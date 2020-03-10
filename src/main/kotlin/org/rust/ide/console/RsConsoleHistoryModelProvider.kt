/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.console

import com.intellij.execution.console.ConsoleHistoryModel
import com.intellij.execution.console.ConsoleHistoryModelProvider
import com.intellij.execution.console.LanguageConsoleView

class RsConsoleHistoryModelProvider : ConsoleHistoryModelProvider {
    override fun createModel(persistenceId: String, consoleView: LanguageConsoleView): ConsoleHistoryModel? {
        return if (consoleView is RsConsoleView) {
            com.intellij.execution.console.createModel(persistenceId, consoleView)
        } else {
            null
        }
    }
}
