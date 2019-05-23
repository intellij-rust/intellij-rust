/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType


/**
 * Fix that converts the given reference to owned value.
 * @param expr An element, that represents a reference from which the first
 * symbol '&' must be removed.
 */
class RemoveRefFix private constructor(
    expr: RsUnaryExpr
) : LocalQuickFixOnPsiElement(expr) {
    override fun getText() = when ((startElement as RsUnaryExpr).operatorType) {
        UnaryOperator.REF -> "Remove &"
        UnaryOperator.REF_MUT -> "Remove &mut"
        else -> error("unreachable")
    }
    override fun getFamilyName() = "Remove reference"

    override fun invoke(project: Project, file: PsiFile, expr: PsiElement, endElement: PsiElement) {
        (expr as RsUnaryExpr).expr?.let { expr.replace(it) }
    }

    companion object {
        fun createIfCompatible(expr: RsExpr): RemoveRefFix? {
            return if(expr is RsUnaryExpr && expr.operatorType in listOf(UnaryOperator.REF, UnaryOperator.REF_MUT)) {
                RemoveRefFix(expr)
            } else {
                null
            }
        }
    }
}
