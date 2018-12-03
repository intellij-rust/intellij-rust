/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement

class RsInlineMethodHandler: InlineActionHandler() {
    override fun isEnabledOnElement(element: PsiElement?): Boolean  = element is RsElement

    override fun isEnabledOnElement(element: PsiElement?, editor: Editor?): Boolean =
        isEnabledOnElement(element)

    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        val function = RsInlineMethodProcessor.getFunction(element)
            ?: TODO("handle no referencing function")

        function.block ?: TODO("handle no method")

        if (RsInlineMethodProcessor.checkMultipleReturns(function)) {
            TODO("cannot inline fun with multiple returns")
        }

        if (RsInlineMethodProcessor.checkRecursiveCall(function)) {
            TODO("cannot inline recursive function")
        }
    }

    private fun showErrorHint(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            RefactoringBundle.message("inline.method.title"),
            HelpID.INLINE_VARIABLE
        )
    }

    override fun isEnabledForLanguage(l: Language?): Boolean = l == RsLanguage

    override fun canInlineElementInEditor(element: PsiElement, editor: Editor?): Boolean =
        canInlineElement(element)

    override fun canInlineElement(element: PsiElement): Boolean =
        element is RsFunction
}
