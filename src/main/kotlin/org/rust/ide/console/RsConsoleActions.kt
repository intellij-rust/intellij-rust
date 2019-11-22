/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil

class RestartAction(private val consoleRunner: RsConsoleRunner) : AnAction() {

    init {
        ActionUtil.copyFrom(this, IdeActions.ACTION_RERUN)
        templatePresentation.icon = AllIcons.Actions.Restart
    }

    override fun actionPerformed(e: AnActionEvent) = consoleRunner.rerun()
}
