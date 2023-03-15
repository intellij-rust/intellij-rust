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
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict

class SurroundWithUnsafeFix(expr: RsExpr) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    override fun getFamilyName() = text
    override fun getText() = "Surround with unsafe block"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, expr: PsiElement, endElement: PsiElement) {
        val target = expr.ancestorStrict<RsExprStmt>() ?: expr
        val unsafeBlockExpr = RsPsiFactory(project).createUnsafeBlockExprOrStmt(target)
        target.replace(unsafeBlockExpr)
    }
}
