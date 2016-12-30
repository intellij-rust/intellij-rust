package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class MatchToIfLetIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Convert match statement to if let"
    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        findMatchExpr(element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val (matchExpr, matchTarget, matchBody, arm) = findMatchExpr(element)
            ?: error("Unavailable intention invoked")

        var bodyText = arm.expr?.text ?: return
        if (arm.expr !is RustBlockExprElement) {
            bodyText = "{\n$bodyText\n}"
        }

        val rustIfLetExprElement =
            RustPsiFactory(project).createExpression("if let ${arm.matchPat.text} = ${matchTarget.text} $bodyText")
                as RustIfExprElement
        matchExpr.replace(rustIfLetExprElement)
    }

    data class Context(
        val match: RustMatchExprElement,
        val matchTarget: RustExprElement,
        val matchBody: RustMatchBodyElement,
        val nonVoidArm: RustMatchArmElement
    )

    private fun findMatchExpr(element: PsiElement): Context? {
        if (!element.isWritable) return null

        val matchExpr = element.parentOfType<RustMatchExprElement>() ?: return null
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

    val RustExprElement.isVoid: Boolean
        get() = (this is RustBlockExprElement && block?.lbrace.getNextNonCommentSibling() == block?.rbrace)
            || this is RustUnitExprElement
}
