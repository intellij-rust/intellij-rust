package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.*

/**
 * Suggest to remove a semicolon in situations like
 *
 * ```
 * fn foo() -> i32 { 92; }
 * ```
 */
class RustExtraSemicolonInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Extra semicolon"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RustElementVisitor() {
            override fun visitImplMethodMember(o: RustImplMethodMemberElement) = inspect(holder, o)
            override fun visitFnItem(o: RustFnItemElement) = inspect(holder, o)
        }
}


private fun inspect(holder: ProblemsHolder, fn: RustFnElement) {
    val block = fn.block ?: return
    val retType = fn.retType?.type ?: return
    if (block.expr != null) return
    val lastStatement = block.stmtList.lastOrNull() as? RustExprStmtElement ?: return
    if (lastStatement.expr is RustRetExprElement) return

    holder.registerProblem(
        lastStatement,
        "Function returns () instead of ${retType.text}",
        object : LocalQuickFix {
            override fun getName(): String = "Remove semicolon"

            override fun getFamilyName(): String = name

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                val statement = (descriptor.psiElement as RustExprStmtElement)
                statement.replace(statement.expr)
            }
        }
    )
}
