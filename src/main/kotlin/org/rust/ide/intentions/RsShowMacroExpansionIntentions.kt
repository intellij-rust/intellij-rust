/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.actions.macroExpansion.MacroExpansionViewDetails
import org.rust.ide.actions.macroExpansion.RsShowMacroExpansionActionBase
import org.rust.ide.actions.macroExpansion.expandMacroForViewWithProgress
import org.rust.ide.actions.macroExpansion.showMacroExpansionPopup
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MacroCall
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MetaItem
import org.rust.lang.core.psi.ext.ancestorMacroCall
import org.rust.lang.core.psi.ext.isAncestorOf
import org.rust.lang.core.psi.ext.kind
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok

abstract class RsShowMacroExpansionIntentionBase(private val expandRecursively: Boolean) :
    RsElementBaseIntentionAction<RsPossibleMacroCall>() {

    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsPossibleMacroCall? {
        val macroCall = element.ancestorMacroCall ?: return null

        val isValidContext = when (val kind = macroCall.kind) {
            is MacroCall -> kind.call.path.isAncestorOf(element) || element == kind.call.excl
            is MetaItem -> true
            else -> error("unreachable")
        }
        if (!isValidContext) return null
        return macroCall
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsPossibleMacroCall) {
        when (val expansionDetails = expandMacroForViewWithProgress(project, ctx, expandRecursively)) {
            is Ok -> showExpansion(project, editor, expansionDetails.ok)
            is Err -> showError(editor, expansionDetails.err)
        }
    }

    /** Progress window cannot be shown in the write action, so it have to be disabled. **/
    override fun startInWriteAction(): Boolean = false

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

class RsShowRecursiveMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = true) {
    override fun getText() = "Show recursive macro expansion"
}

class RsShowSingleStepMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = false) {
    override fun getText() = "Show single step macro expansion"
}
