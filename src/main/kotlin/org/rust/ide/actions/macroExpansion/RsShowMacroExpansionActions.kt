/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.macroExpansion

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.openapiext.editor
import org.rust.openapiext.elementUnderCaretInEditor
import org.rust.openapiext.project

abstract class RsShowMacroExpansionActionBase(private val expandRecursively: Boolean) : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = getMacroUnderCaret(e.dataContext) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        performForContext(e.dataContext)
    }

    @VisibleForTesting
    fun performForContext(e: DataContext) {
        val project = e.project ?: return
        val editor = e.editor ?: return
        val macroToExpand = getMacroUnderCaret(e) ?: return

        val expansionDetails = expandMacroForViewWithProgress(project, macroToExpand, expandRecursively)

        if (expansionDetails != null) {
            showExpansion(project, editor, expansionDetails)
        } else {
            showError(editor)
        }
    }

    /**
     * This method is required for testing to avoid actually creating popup and editor.
     * Inspired by [com.intellij.codeInsight.hint.actions.ShowImplementationsAction].
     */
    @VisibleForTesting
    protected open fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
        showMacroExpansionPopup(project, editor, expansionDetails)
    }

    @VisibleForTesting
    protected open fun showError(editor: Editor) {
        HintManager.getInstance().showErrorHint(editor, FAILED_TO_EXPAND_MESSAGE)
    }
}

/** Action for showing recursive expansion of ordinary macros (not procedural or custom derives). */
class RsShowRecursiveMacroExpansionAction : RsShowMacroExpansionActionBase(expandRecursively = true)

/** Action for showing first-level expansion of ordinary macros (not procedural or custom derives). */
class RsShowSingleStepMacroExpansionAction : RsShowMacroExpansionActionBase(expandRecursively = false)

/** Returns closest [RsMacroCall] under cursor in the [DataContext.editor] if it's present. */
fun getMacroUnderCaret(event: DataContext): RsMacroCall? {
    val elementUnderCaret = event.elementUnderCaretInEditor ?: return null

    return elementUnderCaret.ancestorOrSelf()
}
