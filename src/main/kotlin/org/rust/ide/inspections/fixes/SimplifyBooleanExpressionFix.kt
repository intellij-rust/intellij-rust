/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.BooleanExprSimplifier
import org.rust.ide.utils.isPure
import org.rust.lang.core.psi.RsExpr

class SimplifyBooleanExpressionFix(expr: RsExpr) : LocalQuickFixOnPsiElement(expr) {
    override fun getText(): String = "Simplify boolean expression"
    override fun getFamilyName() = text

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val expr = startElement as? RsExpr ?: return
        if (expr.isPure() == true && BooleanExprSimplifier.canBeSimplified(expr)) {
            val simplified = BooleanExprSimplifier(project).simplify(expr) ?: return
            expr.replace(simplified)
        }
    }
}
