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
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorOrSelf

class AddUnsafeFix(expr: RsBlock) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    private val _text = "Add unsafe to ${if (expr.parent is RsBlockExpr) "block" else "function"}"
    override fun getFamilyName() = text
    override fun getText() = _text

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, expr: PsiElement, endElement: PsiElement) {
        val unsafe = RsPsiFactory(project).createUnsafeKeyword()

        if (expr.parent is RsBlockExpr) {
            expr.parent.addBefore(unsafe, expr)
        } else {
            expr.ancestorOrSelf<RsFunction>()?.let {
                it.addBefore(unsafe, it.fn)
            }
        }
    }
}
