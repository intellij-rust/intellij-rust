/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.arms
import org.rust.lang.core.psi.ext.hasSemicolon
import org.rust.openapiext.Testmark

class RsLiftInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor {
        return object : RsWithMacrosInspectionVisitor() {
            override fun visitIfExpr(o: RsIfExpr) {
                if (o.parent is RsElseBranch) return
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

    override val isSyntaxOnly: Boolean = true

    private fun RsProblemsHolder.register(expr: RsExpr, keyword: PsiElement) {
        val keywordName = keyword.text
        registerProblem(
            expr,
            keyword.textRangeInParent,
            RsBundle.message("inspection.message.return.can.be.lifted.out", keywordName),
            LiftReturnOutFix(expr, keywordName)
        )
    }

    private class LiftReturnOutFix(
        element: RsExpr,
        private val keyword: String
    ) : RsQuickFixBase<RsExpr>(element) {
        override fun getFamilyName(): String = RsBundle.message("intention.family.name.lift.return")
        override fun getText(): String = RsBundle.message("intention.name.lift.return.out", keyword)

        override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
            val foldableReturns = element.getFoldableReturns() ?: return
            val factory = RsPsiFactory(project)
            for (foldableReturn in foldableReturns) {
                foldableReturn.elementToReplace.replaceWithTailExpr(factory.createExpression(foldableReturn.expr.text))
            }
            val parent = element.parent
            if (parent !is RsRetExpr) {
                (parent as? RsMatchArm)?.addCommaIfNeeded(factory)
                element.replace(factory.createRetExpr(element.text))
            } else {
                Testmarks.InsideRetExpr.hit()
            }
        }

        private fun RsMatchArm.addCommaIfNeeded(psiFactory: RsPsiFactory) {
            if (comma != null) return
            val arms = ancestorStrict<RsMatchExpr>()?.arms ?: return
            val index = arms.indexOf(this)
            if (index == -1 || index == arms.size - 1) return
            add(psiFactory.createComma())
        }
    }

    object Testmarks {
        object InsideRetExpr : Testmark()
    }
}

private fun RsElement.replaceWithTailExpr(expr: RsExpr) {
    when (this) {
        is RsExpr -> replace(expr)
        is RsStmt -> {
            val newStmt = RsPsiFactory(project).tryCreateExprStmtWithoutSemicolon("()")!!
            newStmt.expr.replace(expr)
            replace(newStmt)
        }
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
                if (hasSemicolon) {
                    val retExpr = expr as? RsRetExpr ?: return false
                    val expr = retExpr.expr ?: return false
                    result += FoldableElement(expr, this)
                } else {
                    if (!expr.collectFoldableReturns()) return false
                }
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
                val elseIf = elseBranch?.ifExpr
                if (elseIf != null) {
                    if (!elseIf.collectFoldableReturns()) return false
                } else {
                    if (elseBranch?.block?.collectFoldableReturns() != true) return false
                }
            }
            is RsMatchExpr -> {
                val arms = matchBody?.matchArmList ?: return false
                if (arms.isEmpty()) return false
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
