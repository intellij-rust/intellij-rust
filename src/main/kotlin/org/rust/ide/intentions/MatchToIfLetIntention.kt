package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class MatchToIfLetIntention : RsElementBaseIntentionAction<MatchToIfLetIntention.Context>() {
    override fun getText() = "Convert match statement to if let"
    override fun getFamilyName(): String = text

    data class Context(
        val match: RsMatchExpr,
        val matchTarget: RsExpr,
        val matchBody: RsMatchBody,
        val nonVoidArm: RsMatchArm
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = element.parentOfType<RsMatchExpr>() ?: return null
        val matchTarget = matchExpr.expr ?: return null
        val matchBody = matchExpr.matchBody ?: return null
        val matchArmList = matchBody.matchArmList

        val nonVoidArm = matchArmList
            .filter { it.expr?.isVoid == false }
            .singleOrNull() ?: return null

        val pattern = nonVoidArm.matchPat.patList.singleOrNull() ?: return null
        if (pattern.text == "_") return null

        return Context(matchExpr, matchTarget, matchBody, nonVoidArm)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, matchTarget, matchBody, arm) = ctx

        var bodyText = arm.expr?.text ?: return
        if (arm.expr !is RsBlockExpr) {
            bodyText = "{\n$bodyText\n}"
        }

        val rustIfLetExprElement =
            RustPsiFactory(project).createExpression("if let ${arm.matchPat.text} = ${matchTarget.text} $bodyText")
                as RsIfExpr
        matchExpr.replace(rustIfLetExprElement)
    }

    private val RsExpr.isVoid: Boolean
        get() = (this is RsBlockExpr && block?.lbrace.getNextNonCommentSibling() == block?.rbrace)
            || this is RsUnitExpr
}
