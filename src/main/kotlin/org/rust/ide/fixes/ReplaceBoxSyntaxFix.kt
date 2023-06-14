/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUnaryExpr

class ReplaceBoxSyntaxFix(element: RsUnaryExpr): LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "Replace `box` with `Box::new`"

    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, boxExpr: PsiElement, endElement: PsiElement) {
        if (boxExpr !is RsUnaryExpr) return
        if (boxExpr.box == null) return
        val exprText = boxExpr.expr?.text ?: return
        boxExpr.replace(RsPsiFactory(project).createBox(exprText))
    }
}
