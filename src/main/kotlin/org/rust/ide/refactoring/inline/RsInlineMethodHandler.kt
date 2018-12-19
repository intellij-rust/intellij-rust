/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.psi.util.parentOfType
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.resolve.ref.RsPathReference
import org.rust.openapiext.runWriteCommandAction

class RsInlineMethodHandler: InlineActionHandler() {
    override fun isEnabledOnElement(element: PsiElement): Boolean  = canInlineElement(element)

    override fun isEnabledOnElement(element: PsiElement, editor: Editor?): Boolean =
        isEnabledOnElement(element)

    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        val function = element as RsFunction

        val body = function.block
        if (body == null){
            errorHint(project, editor, "cannot inline method with no body")
            return
        }

        val reference = TargetElementUtil.findReference(editor, editor.caretModel.offset)
        val f = element.findElementAt(editor.caretModel.offset)

        if (RsInlineMethodProcessor.checkMultipleReturns(function)) {
            errorHint(project, editor, "cannot inline method with more than one exit points")
            return
        }

        var allowInlineThisOnly = false
        if (RsInlineMethodProcessor.checkRecursiveCall(function)) {
            if (reference != null) {
                allowInlineThisOnly = true
            } else {
                errorHint(project, editor, "cannot inline method with recursive calls")
                return
            }
        }

        if (reference != null && RsInlineMethodProcessor.checkIfLoopCondition(function, reference.element)) {
            errorHint(project, editor, "cannot inline multiline method into \"while\" loop condition")
            return
        }

        // TODO: inline dialog call here


        project.runWriteCommandAction {
            val factory = RsPsiFactory(project)
            RsInlineMethodProcessor.replaceLastExprToStatement(function, factory)

            if (reference != null) {
//                val statement =
                val ref = reference as RsPathReference
                RsInlineMethodProcessor(factory).inlineWithLetBindingsAdded(ref, function, factory)
            }
        }
    }

    override fun isEnabledForLanguage(l: Language?): Boolean = l == RsLanguage

    override fun canInlineElementInEditor(element: PsiElement, editor: Editor?): Boolean = canInlineElement(element)

    override fun canInlineElement(element: PsiElement): Boolean =
        element is RsFunction && element.navigationElement is RsFunction

    private fun errorHint(project: Project, editor: Editor, message: String) {
//        CommonRefactoringUtil.showErrorHint(project, editor, message, "Rs Inline Method", "refactoring.inlineMethod")
        // TODO: figure out how to display an error in more correct way
        Messages.showErrorDialog(project, message, "method inline is not possible")
    }

}
