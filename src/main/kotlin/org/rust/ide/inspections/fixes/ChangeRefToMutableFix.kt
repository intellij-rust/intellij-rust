/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType


/**
 * Fix that converts the given immutable reference to a mutable reference.
 * @param expr An element, that represents an immutable reference.
 */
class ChangeRefToMutableFix(expr: RsElement) : LocalQuickFixOnPsiElement(expr) {
    override fun getText() = "Change reference to mutable"
    override fun getFamilyName() = text

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val ref = startElement as? RsUnaryExpr ?: return
        if (ref.operatorType != UnaryOperator.REF) return
        val innerExpr = ref.expr ?: return

        val mutableExpr = RsPsiFactory(project).tryCreateExpression("&mut ${innerExpr.text}") ?: return
        startElement.replace(mutableExpr)
    }
}
