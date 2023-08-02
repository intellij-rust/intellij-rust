/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWindow
import org.rust.openapiext.isDispatchThread

/**
 * A hack that let us know whether [GotoDeclarationAction] is now executes or not
 */
@Service
class RsGoToDeclarationRunningService {
    @Volatile
    private var _isGoToDeclarationAction: Boolean = false

    val isGoToDeclarationAction: Boolean
        get() = _isGoToDeclarationAction && (isDispatchThread || ProgressManager.getGlobalProgressIndicator() is ProgressWindow)

    @Suppress("unused")
    private class Listener : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
            if (action.isGoToDeclaration) {
                getInstance()._isGoToDeclarationAction = true
            }
        }

        override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
            if (action.isGoToDeclaration) {
                getInstance()._isGoToDeclarationAction = false
            }
        }

        private val AnAction.isGoToDeclaration: Boolean
            get() = this is GotoDeclarationAction
    }

    companion object {
        fun getInstance(): RsGoToDeclarationRunningService = service()
    }
}
