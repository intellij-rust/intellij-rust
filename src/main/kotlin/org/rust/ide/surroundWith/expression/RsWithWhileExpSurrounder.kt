/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsWhileExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.type

class RsWithWhileExpSurrounder : RsExpressionSurrounderBase<RsWhileExpr>() {
    override fun getTemplateDescription(): String = "while expr"

    override fun createTemplate(project: Project): RsWhileExpr =
        RsPsiFactory(project).createExpression("while a {stmnt;}") as RsWhileExpr

    override fun getWrappedExpression(expression: RsWhileExpr): RsExpr =
        expression.condition!!.expr

    override fun isApplicable(expression: RsExpr): Boolean =
        expression.type == TyBool

    override fun doPostprocessAndGetSelectionRange(editor: Editor, expression: PsiElement): TextRange? {
        var block = (expression as? RsWhileExpr)?.block ?: return null
        block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(block)
        val rbrace = checkNotNull(block.rbrace) {
            "Incomplete block in while surrounder"
        }

        val offset = block.lbrace.textOffset + 1
        editor.document.deleteString(offset, rbrace.textOffset)
        return TextRange.from(offset, 0)
    }
}
