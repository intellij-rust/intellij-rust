/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.actions.macroExpansion.MacroExpansionViewDetails
import org.rust.ide.actions.macroExpansion.expandMacroForViewWithProgress
import org.rust.ide.actions.macroExpansion.showMacroExpansionPopup
import org.rust.lang.core.psi.RsMacroCall

abstract class RsShowMacroExpansionIntentionBase(private val expandRecursively: Boolean) :
    RsElementBaseIntentionAction<RsMacroCall>() {

    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMacroCall? =
        element.parentOfType<RsMacroCall>()

    override fun invoke(project: Project, editor: Editor, ctx: RsMacroCall) {
        val expansionDetails = expandMacroForViewWithProgress(project, ctx, expandRecursively)

        showExpansion(project, editor, expansionDetails)
    }

    /**
     * This method is required for testing to avoid actually creating popup and editor.
     * Inspired by [com.intellij.codeInsight.hint.actions.ShowImplementationsAction].
     */
    @VisibleForTesting
    protected open fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
        showMacroExpansionPopup(project, editor, expansionDetails)
    }

}

class RsShowRecursiveMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = true) {
    override fun getText() = "Show recursive macro expansion"
}

class RsShowSingleStepMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = false) {
    override fun getText() = "Show single step macro expansion"
}
