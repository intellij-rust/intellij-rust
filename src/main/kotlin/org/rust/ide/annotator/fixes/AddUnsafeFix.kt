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
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory

class AddUnsafeFix(expr: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    private val _text = "Add unsafe to ${if (expr is RsBlockExpr) "block" else "function" }"
    override fun getFamilyName() = text
    override fun getText() = _text

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, expr: PsiElement, endElement: PsiElement) {
        val unsafe = RsPsiFactory(project).createUnsafeKeyword()
        when (expr) {
            is RsBlockExpr -> expr.addBefore(unsafe, expr.block)
            is RsFunction -> expr.addBefore(unsafe, expr.fn)
        }
    }
}
