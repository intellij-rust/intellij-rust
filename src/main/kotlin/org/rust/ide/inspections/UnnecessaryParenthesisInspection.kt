package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustIfExpr
import org.rust.lang.core.psi.RustVisitor
import org.rust.lang.core.psi.impl.RustParenExprImpl

/**
 * Created by zjh on 16/3/8.
 */
class UnnecessaryParenthesisInspection : RustLocalInspectionTool() {
    override fun getDisplayName() = "Unnecessary Parenthesis"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustVisitor() {
            override fun visitIfExpr(expr: RustIfExpr) {
                val exprText = expr.expr?.text
                if (exprText?.startsWith("(")!! && exprText?.endsWith(")")!!) {
                    holder.registerProblem(expr.expr!!, "Unnecessary Parenthesis", RemoveParenthesis);
                }
            }
        }

    object RemoveParenthesis : LocalQuickFixBase("Remove parenthesis") {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expr = descriptor.psiElement as RustParenExprImpl
            expr.lparen.delete()
            expr.rparen.delete()
        }
    }
}
