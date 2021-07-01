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
import org.rust.ide.refactoring.findBinding
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

class MatchToIfLetIntention : RsElementBaseIntentionAction<MatchToIfLetIntention.Context>() {
    override fun getText() = RsBundle.message("intention.name.convert.match.statement.to.if.let")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val match: RsMatchExpr,
        val matchTarget: RsExpr,
        val matchBody: RsMatchBody
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null
        if (element != matchExpr.match) return null
        val matchTarget = matchExpr.expr ?: return null
        val matchBody = matchExpr.matchBody ?: return null
        if (matchBody.matchArmList.isEmpty()) return null
        if (matchBody.matchArmList.any { it.matchArmGuard != null || it.outerAttrList.isNotEmpty() }) return null

        if (!PsiModificationUtil.canReplace(matchExpr)) return null
        return Context(matchExpr, matchTarget, matchBody)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val arms = ctx.matchBody.matchArmList
        val lastArm = arms.lastOrNull() ?: return

        // else is required for `if` expressions if the resulting type is not ()
        val hasUnitType = ctx.match.type is TyUnit
        val lastArmHasBinding = lastArm.pat.findBinding() != null

        fun isExprEmpty(expr: RsExpr?): Boolean {
            return expr?.let {
                when {
                    it is RsUnitExpr -> true
                    it is RsBlockExpr && it.block.children.isEmpty() -> true
                    else -> false
                }
            } ?: false
        }

        val lastArmExprEmpty = isExprEmpty(lastArm.expr)

        val text = buildString {
            var hasElseBlock = false

            for ((index, arm) in arms.withIndex()) {
                val expr = arm.expr ?: continue

                var armText = "if let ${arm.pat.text} = ${ctx.matchTarget.text}"
                when (index) {
                    // First, nothing special
                    0 -> {}
                    // Last, handle else block
                    arms.size - 1 -> {
                        // We don't need an else block and the last arm is empty, skip it
                        if (hasUnitType && lastArmExprEmpty) {
                            break
                        }
                        // We can coerce the last arm to an else block, because it has no bindings
                        else if (!lastArmHasBinding) {
                            armText = " else"
                            hasElseBlock = true
                        } else {
                            armText = " else $armText"
                        }
                    }
                    else -> armText = " else $armText"
                }
                append(armText)
                append(' ')

                val innerExprText = when {
                    !hasUnitType && isExprEmpty(expr) -> "unreachable!()"
                    expr is RsBlockExpr -> {
                        val text = expr.block.text
                        val start = expr.block.lbrace.startOffsetInParent + 1
                        val end = expr.block.rbrace?.startOffsetInParent ?: text.length
                        text.substring(start, end)
                    }
                    else -> expr.text
                }

                val exprText = "{\n    $innerExprText\n}"
                append(exprText)
            }

            // We need to add an extra else arm in this case
            if (!hasUnitType && !hasElseBlock) {
                append(" else {\n    unreachable!()\n}")
            }
        }

        val factory = RsPsiFactory(project)
        val ifLetExpr = factory.createExpression(text)
        ctx.match.replace(ifLetExpr)
    }
}
