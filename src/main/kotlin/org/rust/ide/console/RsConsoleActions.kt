/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.actions.EOFAction
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction

class RestartAction(private val consoleRunner: RsConsoleRunner) : AnAction() {

    init {
        ActionUtil.copyFrom(this, IdeActions.ACTION_RERUN)
        templatePresentation.icon = AllIcons.Actions.Restart
    }

    override fun actionPerformed(e: AnActionEvent) = consoleRunner.rerun()
}

class StopAction(private val processHandler: RsConsoleProcessHandler)
    : DumbAwareAction("Stop Console", "Stop Rust Console", AllIcons.Actions.Suspend) {

    init {
        val eofAction = ActionManager.getInstance().getAction(EOFAction.ACTION_ID)
        copyShortcutFrom(eofAction)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !processHandler.isProcessTerminated
    }

    override fun actionPerformed(e: AnActionEvent) {
        processHandler.destroyProcess()
    }
}

class SoftWrapAction(private val consoleView: RsConsoleView) :
    ToggleAction(
        ActionsBundle.actionText("EditorToggleUseSoftWraps"),
        ActionsBundle.actionDescription("EditorToggleUseSoftWraps"),
        AllIcons.Actions.ToggleSoftWrap
    ), DumbAware {

    private var isSelected: Boolean = false

    init {
        updateEditors()
    }

    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    private fun updateEditors() {
        consoleView.editor.settings.isUseSoftWraps = isSelected
        consoleView.consoleEditor.settings.isUseSoftWraps = isSelected
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        isSelected = state
        updateEditors()
    }
}

class PrintAction(private val consoleView: RsConsoleView) : DumbAwareAction() {

    private val printAction = ActionManager.getInstance().getAction("Print")

    init {
        ActionUtil.copyFrom(this, "Print")
    }

    override fun update(e: AnActionEvent) {
        printAction.update(createActionEvent(e))
    }

    override fun actionPerformed(e: AnActionEvent) {
        printAction.actionPerformed(createActionEvent(e))
    }

    private fun createActionEvent(e: AnActionEvent): AnActionEvent {
        val dataContext = ConsoleDataContext(e.dataContext, consoleView)
        return AnActionEvent(e.inputEvent, dataContext, e.place, e.presentation, e.actionManager, e.modifiers)
    }

    private class ConsoleDataContext(
        private val myOriginalDataContext: DataContext,
        private val consoleView: RsConsoleView
    ) : DataContext {

        override fun getData(dataId: String): Any? {
            return if (CommonDataKeys.EDITOR.`is`(dataId)) {
                consoleView.editor
            } else {
                myOriginalDataContext.getData(dataId)
            }
        }
    }
}
