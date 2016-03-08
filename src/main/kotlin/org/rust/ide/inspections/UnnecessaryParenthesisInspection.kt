package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustIfExpr
import org.rust.lang.core.psi.RustVisitor

/**
 * Created by zjh on 16/3/8.
 */
class UnnecessaryParenthesisInspection : RustLocalInspectionTool() {
    override fun getDisplayName() = "Unnecessary Parenthesis"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustVisitor() {
            override fun visitIfExpr(o: RustIfExpr) {
                val exprText = o.expr?.text
                if (exprText?.startsWith("(")!! && exprText?.endsWith(")")!!) {
                    holder.registerProblem(o.expr!!, "Unnecessary Parenthesis")
                }
            }
        }
}
