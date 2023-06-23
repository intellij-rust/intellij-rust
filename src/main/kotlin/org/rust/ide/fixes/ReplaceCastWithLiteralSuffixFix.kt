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
import org.rust.lang.core.psi.RsCastExpr
import org.rust.lang.core.psi.RsPsiFactory

open class ReplaceCastWithLiteralSuffixFix(
    element: RsCastExpr
) : RsQuickFixBase<RsCastExpr>(element) {
    private val fixText: String = "Replace with `${element.expr.text}${element.typeReference.text}`"
    override fun getFamilyName(): String = "Replace cast with literal suffix"
    override fun getText(): String = fixText
    override fun invoke(project: Project, editor: Editor?, element: RsCastExpr) {
        val psiFactory = RsPsiFactory(project)
        element.replace(psiFactory.createExpression(element.expr.text + element.typeReference.text))
    }
}
