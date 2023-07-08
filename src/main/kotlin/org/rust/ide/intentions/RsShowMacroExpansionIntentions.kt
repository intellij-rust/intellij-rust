/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.actions.macroExpansion.MacroExpansionViewDetails
import org.rust.ide.actions.macroExpansion.RsShowMacroExpansionActionBase
import org.rust.ide.actions.macroExpansion.expandMacroForViewWithProgress
import org.rust.ide.actions.macroExpansion.showMacroExpansionPopup
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.macros.findExpansionElementOrSelf
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MacroCall
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MetaItem
import org.rust.lang.core.psi.ext.contextMacroCall
import org.rust.lang.core.psi.ext.isContextOf
import org.rust.lang.core.psi.ext.kind
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok

class RsShowRecursiveMacroExpansionIntention : RsShowMacroExpansionIntentionBase() {
    override val expandRecursively: Boolean get() = true
    override fun getText() = RsBundle.message("intention.name.show.recursive.macro.expansion")
}

class RsShowSingleStepMacroExpansionIntention : RsShowMacroExpansionIntentionBase() {
    override val expandRecursively: Boolean get() = false
    override fun getText() = RsBundle.message("intention.name.show.single.step.macro.expansion")
}

abstract class RsShowMacroExpansionIntentionBase : RsElementBaseIntentionAction<RsPossibleMacroCall>() {
    protected abstract val expandRecursively: Boolean

    override fun getFamilyName() = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL
    override val functionLikeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsPossibleMacroCall? {
        val possiblyExpandedElement = element.findExpansionElementOrSelf()
        val macroCall = possiblyExpandedElement.contextMacroCall ?: return null

        val isValidContext = when (val kind = macroCall.kind) {
            is MacroCall -> kind.call.path.isContextOf(possiblyExpandedElement)
                || possiblyExpandedElement == kind.call.excl
            is MetaItem -> true
        }
        if (!isValidContext) return null
        return macroCall
    }

    /** Progress window cannot be shown in the write action, so it have to be disabled. **/
    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, ctx: RsPossibleMacroCall) {
        when (val expansionDetails = expandMacroForViewWithProgress(project, ctx, expandRecursively)) {
            is Ok -> showExpansion(project, editor, expansionDetails.ok)
            is Err -> showError(editor, expansionDetails.err)
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

    private fun showError(editor: Editor, error: GetMacroExpansionError) {
        RsShowMacroExpansionActionBase.showMacroExpansionError(editor, error)
    }
}
