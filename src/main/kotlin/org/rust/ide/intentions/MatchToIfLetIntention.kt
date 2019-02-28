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
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.isIrrefutable

class MatchToIfLetIntention : RsElementBaseIntentionAction<MatchToIfLetIntention.Context>() {
    override fun getText() = "Convert match statement to if let"
    override fun getFamilyName(): String = text

    data class Context(
        val match: RsMatchExpr,
        val matchTarget: RsExpr,
        val nonVoidArm: RsMatchArm,
        val orPats: RsOrPats
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null
        val matchTarget = matchExpr.expr ?: return null
        val matchBody = matchExpr.matchBody ?: return null
        val matchArmList = matchBody.matchArmList

        val nonVoidArm = matchArmList.singleOrNull { it.expr?.isVoid == false } ?: return null
        if (nonVoidArm.matchArmGuard != null || nonVoidArm.outerAttrList.isNotEmpty()) return null
        val orPats = nonVoidArm.orPats
        val pats = orPats.patList
        if (pats.size == 1 && pats[0].isIrrefutable) return null

        return Context(matchExpr, matchTarget, nonVoidArm, orPats)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, matchTarget, arm, orPats) = ctx

        var bodyText = arm.expr?.text ?: return
        if (arm.expr !is RsBlockExpr) {
            bodyText = "{\n$bodyText\n}"
        }

        val rustIfLetExprElement =
            RsPsiFactory(project).createExpression("if let ${orPats.text} = ${matchTarget.text} $bodyText")
                as RsIfExpr
        matchExpr.replace(rustIfLetExprElement)
    }

    private val RsExpr.isVoid: Boolean
        get() = (this is RsBlockExpr && block.lbrace.getNextNonCommentSibling() == block.rbrace)
            || this is RsUnitExpr
}
