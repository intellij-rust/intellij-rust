/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsParenExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.type

class RsWithNotSurrounder : RsExpressionSurrounderBase<RsUnaryExpr>() {
    override fun getTemplateDescription(): String = "!(expr)"

    override fun createTemplate(project: Project): RsUnaryExpr =
        RsPsiFactory(project).createExpression("!(a)") as RsUnaryExpr

    override fun getWrappedExpression(expression: RsUnaryExpr): RsExpr =
        (expression.expr as RsParenExpr).expr

    override fun isApplicable(expression: RsExpr): Boolean =
        expression.type == TyBool

    override fun doPostprocessAndGetSelectionRange(editor: Editor, expression: PsiElement): TextRange {
        val offset = expression.textRange.endOffset
        return TextRange.from(offset, 0)
    }
}
