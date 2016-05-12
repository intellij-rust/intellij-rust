package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustBinaryExpr
import org.rust.lang.core.psi.RustIfExpr
import org.rust.lang.core.psi.RustParenExpr
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.RustParenExprImpl
import org.rust.lang.core.psi.util.parentRelativeRange

class UnnecessaryParenthesisInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Unnecessary parenthesis"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustVisitor() {
            override fun visitParenExpr(expr: RustParenExpr) {
                val subExpr = expr.expr
                when (subExpr) {
                    !is RustBinaryExpr -> {
                        if (expr.parent !is RustIfExpr)
                            holder.registerProblem(expr, "Redundant parenthesis around unambiguous expression", RemoveParenthesis)
                    }
                }
            }

            override fun visitIfExpr(expr: RustIfExpr) {
                for (e in expr.exprList) {
                    // Why is this a... list of expressions? Under what circumstances can an if have more than one?
                    if (e is RustParenExpr) {
                        holder.registerProblem(expr, e.parentRelativeRange, "Unnecessary parenthesis in if predicate expression", RemoveIfExprParenthesis)
                        break
                    }
                }
            }
        }
    }

    object RemoveParenthesis : LocalQuickFixBase("Remove parenthesis in expression") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expr = descriptor.psiElement as? RustParenExprImpl
            if (expr != null) {
                expr.lparen.delete()
                expr.rparen.delete()
            }
        }
    }

    object RemoveIfExprParenthesis : LocalQuickFixBase("Remove parenthesis in if predicate expression") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val ifExpr = descriptor.psiElement as? RustIfExpr
            val subExprList = ifExpr?.exprList
            if (subExprList != null) {
                for (e in subExprList) {
                    if (e is RustParenExpr) {
                        e.lparen.delete()
                        e.rparen.delete()
                        break
                    }
                }
            }
        }
    }
}
