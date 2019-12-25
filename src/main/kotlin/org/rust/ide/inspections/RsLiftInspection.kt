/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement

class RsLiftInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        return object : RsVisitor() {
            override fun visitIfExpr(o: RsIfExpr) {
                checkExpr(o, o.`if`)
            }

            override fun visitMatchExpr(o: RsMatchExpr) {
                checkExpr(o, o.match)
            }

            private fun checkExpr(e: RsExpr, keyword: PsiElement) {
                if (e.hasFoldableReturns) {
                    holder.register(e, keyword)
                }
            }
        }
    }

    private fun RsProblemsHolder.register(expr: RsExpr, keyword: PsiElement) {
        val keywordName = keyword.text
        registerProblem(expr, keyword.textRangeInParent,
            "Return can be lifted out of '$keywordName'", LiftReturnOutFix(keywordName))
    }

    private class LiftReturnOutFix(private val keyword: String) : LocalQuickFix {
        override fun getName(): String = "Lift return out of '$keyword'"
        override fun getFamilyName(): String = "Lift return"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expr = descriptor.psiElement as RsExpr
            val foldableReturns = expr.getFoldableReturns() ?: return
            val factory = RsPsiFactory(project)
            for (foldableReturn in foldableReturns) {
                foldableReturn.elementToReplace.replace(factory.createExpression(foldableReturn.expr.text))
            }
            if (expr.parent !is RsRetExpr) {
                expr.replace(factory.createRetExpr(expr.text))
            } else {
                Testmarks.insideRetExpr.hit()
            }
        }
    }

    object Testmarks {
        val insideRetExpr = Testmark("insideRetExpr")
    }
}

private data class FoldableElement(val expr: RsExpr, val elementToReplace: RsElement)

private val RsExpr.hasFoldableReturns: Boolean get() = getFoldableReturns() != null

private fun RsExpr.getFoldableReturns(): List<FoldableElement>? {
    val result = mutableListOf<FoldableElement>()

    fun RsElement.collectFoldableReturns(): Boolean {
        when (this) {
            is RsRetExpr -> {
                val expr = expr ?: return false
                result += FoldableElement(expr, this)
            }
            is RsExprStmt -> {
                val retExpr = expr as? RsRetExpr ?: return false
                val expr = retExpr.expr ?: return false
                result += FoldableElement(expr, this)
            }
            is RsBlock -> {
                val lastChild = children.lastOrNull() as? RsElement ?: return false
                if (!lastChild.collectFoldableReturns()) return false
            }
            is RsBlockExpr -> {
                if (!block.collectFoldableReturns()) return false
            }
            is RsIfExpr -> {
                if (block?.collectFoldableReturns() != true) return false
                if (elseBranch?.block?.collectFoldableReturns() != true) return false
            }
            is RsMatchExpr -> {
                val arms = matchBody?.matchArmList ?: return false
                for (arm in arms) {
                    if (arm.expr?.collectFoldableReturns() != true) return false
                }
            }
            else -> return false
        }
        return true
    }

    return if (collectFoldableReturns()) result else null
}
