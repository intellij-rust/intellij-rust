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
import org.rust.lang.core.types.ty.TyNumeric
import org.rust.lang.core.types.type

class CompareWithZeroFix private constructor(expr: RsCastExpr) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    override fun getFamilyName(): String = "Compare with zero"

    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsCastExpr) return
        startElement.replace(RsPsiFactory(project).createExpression("${startElement.expr.text} != 0"))
    }

    companion object {
        fun createIfCompatible(expression: RsCastExpr): CompareWithZeroFix? {
            return if (expression.expr.type is TyNumeric) CompareWithZeroFix(expression) else null
        }
    }
}
