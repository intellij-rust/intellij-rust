/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict

class MatchToIfLetIntention : RsElementBaseIntentionAction<MatchToIfLetIntention.Context>() {
    override fun getText() = "Convert match statement to if let"
    override fun getFamilyName(): String = text

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

        return Context(matchExpr, matchTarget, matchBody)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val arms = ctx.matchBody.matchArmList
        val lastArm = arms.lastOrNull() ?: return
        val lastArmPatWild = lastArm.pat is RsPatWild
        val lastArmExprEmpty = lastArm.expr?.let {
            when {
                it is RsUnitExpr -> true
                it is RsBlockExpr && it.block.children.isEmpty() -> true
                else -> false
            }
        } ?: false

        val text = buildString {
            for ((index, arm) in arms.withIndex()) {
                var armText = "if let ${arm.pat.text} = ${ctx.matchTarget.text}"
                if (index > 0) {
                    val lastIndex = index == arms.size - 1
                    armText = when {
                        lastIndex && lastArmExprEmpty -> break
                        lastIndex && lastArmPatWild -> " else"
                        else -> " else $armText"
                    }
                }
                append(armText)

                val expr = arm.expr ?: continue

                val innerExprText = if (expr is RsBlockExpr) {
                    val text = expr.block.text
                    val start = expr.block.lbrace.startOffsetInParent + 1
                    val end = expr.block.rbrace?.startOffsetInParent ?: text.length
                    text.substring(start, end)
                } else {
                    expr.text
                }

                val exprText = "{\n    $innerExprText\n}"
                append(exprText)
            }
        }

        val factory = RsPsiFactory(project)
        val ifLetExpr = factory.createExpression(text)
        ctx.match.replace(ifLetExpr)
    }
}
