/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type
import kotlin.math.max
import kotlin.math.min

class UnwrapSingleExprIntention : RsElementBaseIntentionAction<UnwrapSingleExprIntention.Context>() {
    override fun getFamilyName() = "Remove braces from single expression"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val blockExpr = element.ancestorStrict<RsBlockExpr>() ?: return null
        if (blockExpr.isUnsafe || blockExpr.isAsync || blockExpr.isTry) return null
        val block = blockExpr.block

        val singleStatement = block.expandedStmtsAndTailExpr.first.singleOrNull()
        val expr = block.expr
        return when {
            expr != null && block.lbrace.getNextNonCommentSibling() == expr -> {
                text = "Remove braces from single expression"
                Context(blockExpr, expr)
            }
            expr == null && singleStatement is RsExprStmt && singleStatement.expr.type is TyUnit -> {
                text = "Remove braces from single expression statement"
                Context(blockExpr, singleStatement.expr)
            }
            else -> null
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val parent = ctx.blockExpr.parent
        if (parent is RsMatchArm && parent.comma == null) {
            parent.add(RsPsiFactory(project).createComma())
        }

        val element = ctx.expr
        val relativeCaretPosition = min(max(editor.caretModel.offset - element.textOffset, 0), element.textLength)

        val offset = (ctx.blockExpr.replace(element) as RsExpr).textOffset
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)
    }

    data class Context(val blockExpr: RsBlockExpr, val expr: RsExpr)
}
