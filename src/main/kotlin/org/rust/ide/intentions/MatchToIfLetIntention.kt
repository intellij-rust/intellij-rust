package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class MatchToIfLetIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Convert math statement to if let"
    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        findMathExpr(element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val matchExpr = checkNotNull(findMathExpr(element))
        val matchBody = checkNotNull(matchExpr.matchBody)
        val arm = matchBody.matchArmList.find { it.expr?.isVoid == false } ?: return
        var bodyText = arm.expr?.text ?: return
        if (arm.expr !is RustBlockExprElement) {
            bodyText = "{\n$bodyText\n}"
        }

        val rustIfLetExprElement =
            RustPsiFactory(project).createExpression("if let ${arm.matchPat.text} = ${matchExpr.expr.text} $bodyText")
                as RustIfLetExprElement
        matchExpr.replace(rustIfLetExprElement)
    }

    private fun findMathExpr(element: PsiElement): RustMatchExprElement? {
        if (!element.isWritable) return null

        val matchExpr = element.parentOfType<RustMatchExprElement>() ?: return null
        val matchBody = matchExpr.matchBody
        val matchArmList = matchBody?.matchArmList ?: return null

        val notVoidArms: List<RustMatchArmElement> = matchArmList.filter { it.expr?.isVoid == false }
        if (notVoidArms.size == 1 &&
            notVoidArms[0].matchPat.patList.size == 1 &&
            notVoidArms[0].matchPat.patList[0].text != "_") {
            return matchExpr
        } else {
            return null
        }
    }

    val RustExprElement.isVoid: Boolean
        get() = (this is RustBlockExprElement && block?.lbrace.getNextNonCommentSibling() == block?.rbrace)
            || this is RustUnitExprElement
}
