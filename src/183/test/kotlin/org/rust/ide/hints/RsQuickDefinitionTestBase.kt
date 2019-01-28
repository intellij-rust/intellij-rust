/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hint.ImplementationViewComponent
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsTestBase

abstract class RsQuickDefinitionTestBase : RsTestBase() {

    protected fun performShowImplementationAction(): String? {
        var actualText: String? = null

        val action = object : ShowImplementationsAction() {
            override fun showImplementations(impls: Array<out PsiElement>, project: Project, text: String?, editor: Editor?, file: PsiFile?, element: PsiElement?, invokedFromEditor: Boolean, invokedByShortcut: Boolean) {
                if (impls.isEmpty()) return
                actualText = ImplementationViewComponent.getNewText(impls[0].navigationElement)
            }
        }

        action.performForContext((myFixture.editor as EditorEx).dataContext)
        return actualText
    }
}
