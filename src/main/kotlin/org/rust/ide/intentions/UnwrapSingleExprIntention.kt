/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type
import org.rust.openapiext.moveCaretToOffset
import kotlin.math.max
import kotlin.math.min

class UnwrapSingleExprIntention : RsElementBaseIntentionAction<UnwrapSingleExprIntention.Context>() {
    override fun getFamilyName() = RsBundle.message("intention.name.remove.braces.from.single.expression")

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(val blockExpr: RsBlockExpr, val expr: RsExpr)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val blockExpr = element.ancestorStrict<RsBlockExpr>() ?: return null
        if (blockExpr.isUnsafe || blockExpr.isAsync || blockExpr.isTry || blockExpr.isConst) return null
        val block = blockExpr.block

        val singleStatement = block.singleStmt() as? RsExprStmt ?: return null
        if (!PsiModificationUtil.canReplace(blockExpr)) return null
        return when {
            singleStatement.isTailStmt -> {
                text = RsBundle.message("intention.name.remove.braces.from.single.expression")
                Context(blockExpr, singleStatement.expr)
            }
            singleStatement.expr.type is TyUnit -> {
                text = RsBundle.message("intention.name.remove.braces.from.single.expression.statement")
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

        val insertedElement = ctx.blockExpr.replace(element) as RsExpr
        editor.moveCaretToOffset(insertedElement, insertedElement.textOffset + relativeCaretPosition)
    }
}
