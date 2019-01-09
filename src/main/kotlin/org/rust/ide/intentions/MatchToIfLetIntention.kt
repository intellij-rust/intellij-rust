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
import org.rust.lang.core.psi.ext.matchStdOptionOrResult
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

class MatchToIfLetIntention : RsElementBaseIntentionAction<MatchToIfLetIntention.Context>() {
    override fun getText() = "Convert match statement to if let"
    override fun getFamilyName(): String = text

    data class Context(
        val match: RsMatchExpr,
        val matchTarget: RsExpr,
        val nonVoidArm: RsMatchArm,
        val pattern: RsPat
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.ancestorStrict<RsMatchExpr>() ?: return null
        val matchTarget = matchExpr.expr ?: return null
        val matchBody = matchExpr.matchBody ?: return null
        val matchArmList = matchBody.matchArmList

        val nonVoidArm = matchArmList.singleOrNull { it.expr?.isVoid == false } ?: return null
        if (nonVoidArm.matchArmGuard != null || nonVoidArm.outerAttrList.isNotEmpty()) return null

        val pattern = nonVoidArm.patList.singleOrNull() ?: return null
        val item = (matchTarget.type as? TyAdt)?.item as? RsEnumItem
        if (!isRefutablePattern(item, pattern)) return null

        return Context(matchExpr, matchTarget, nonVoidArm, pattern)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, matchTarget, arm, pattern) = ctx

        var bodyText = arm.expr?.text ?: return
        if (arm.expr !is RsBlockExpr) {
            bodyText = "{\n$bodyText\n}"
        }

        val rustIfLetExprElement =
            RsPsiFactory(project).createExpression("if let ${pattern.text} = ${matchTarget.text} $bodyText")
                as RsIfExpr
        matchExpr.replace(rustIfLetExprElement)
    }

    private val RsExpr.isVoid: Boolean
        get() = (this is RsBlockExpr && block.lbrace.getNextNonCommentSibling() == block.rbrace)
            || this is RsUnitExpr
}

private fun isRefutablePattern(item: RsEnumItem?, pattern: RsPat): Boolean = !pattern.isIrrefutable || matchStdOptionOrResult(item, listOf(pattern))
