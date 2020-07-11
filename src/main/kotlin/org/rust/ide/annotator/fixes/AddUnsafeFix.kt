/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory

class AddUnsafeFix private constructor(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    private val _text = "Add unsafe to ${if (element is RsBlockExpr) "block" else "function"}"
    override fun getFamilyName() = text
    override fun getText() = _text

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, element: PsiElement, endElement: PsiElement) {
        val unsafe = RsPsiFactory(project).createUnsafeKeyword()

        when (element) {
            is RsBlockExpr -> element.addBefore(unsafe, element.block)
            is RsFunction -> element.addBefore(unsafe, element.fn)
            else -> error("unreachable")
        }
    }

    companion object {
        fun create(element: PsiElement): AddUnsafeFix? {
            val parent = PsiTreeUtil.getParentOfType(element, RsBlockExpr::class.java, RsFunction::class.java)
                ?: return null
            return AddUnsafeFix(parent)
        }
    }
}
