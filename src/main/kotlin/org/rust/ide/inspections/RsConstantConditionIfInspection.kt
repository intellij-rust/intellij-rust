package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.prevLeaf
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.asBool
import org.rust.lang.utils.evaluation.evaluate

/** See also [RsRedundantElseInspection]. */
class RsConstantConditionIfInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsVisitor() {
            override fun visitIfExpr(ifExpr: RsIfExpr) {
                val condition = ifExpr.condition ?: return
                if (condition.pat != null) return
                val conditionValue = condition.expr?.evaluate()?.asBool() ?: return

                val isUsedAsExpression = ifExpr.isUsedAsExpression()
                val isInsideCascadeIf = ifExpr.parent is RsElseBranch
                val fix = if (!conditionValue && ifExpr.elseBranch == null) {
                    if (isUsedAsExpression && !isInsideCascadeIf) return
                    createDeleteElseBranchFix(ifExpr, isInsideCascadeIf)
                } else {
                    SimplifyFix(conditionValue, isUsedAsExpression, isInsideCascadeIf)
                }

                holder.registerProblem(condition, "Condition is always ''$conditionValue''", fix)
            }
        }

    private fun createDeleteElseBranchFix(ifExpr: RsIfExpr, isInsideCascadeIf: Boolean): SubstituteTextFix {
        val ifRange = ifExpr.rangeWithPrevSpace
        val deletionRange = if (isInsideCascadeIf) {
            val parentElse = (ifExpr.parent as RsElseBranch).`else`
            val elseRange = parentElse.rangeWithPrevSpace(parentElse.prevLeaf())
            elseRange.union(ifRange)
        } else {
            ifRange
        }
        return SubstituteTextFix.delete(
            "Delete expression",
            ifExpr.containingFile,
            deletionRange
        )
    }
}

private class SimplifyFix(
    private val conditionValue: Boolean,
    private val isUsedAsExpression: Boolean,
    private val isInsideCascadeIf: Boolean,
) : LocalQuickFix {
    override fun getFamilyName(): String = name

    override fun getName(): String = "Simplify expression"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val factory = RsPsiFactory(project)
        val ifExpr = descriptor.psiElement.ancestorStrict<RsIfExpr>() ?: return

        // `if false {} else if ... {} else ...`
        ifExpr.elseBranch?.ifExpr?.let { elseIfExpr ->
            if (!conditionValue) {
                ifExpr.replace(elseIfExpr)
                return
            }
        }

        // fn main() {
        //     if true { 1 } else { 0 }`
        //     func();
        // }
        if (!isUsedAsExpression && ifExpr.parent is RsExprStmt) run {
            val tailExpr = ifExpr.block?.expr ?: return@run
            val tailStmt = factory.tryCreateExprStmt(tailExpr.text) ?: return@run
            tailExpr.replace(tailStmt)
        }

        val branch = (if (conditionValue) ifExpr.block else ifExpr.elseBranch?.block) ?: return
        val branchFirst = branch.lbrace.getNextNonWhitespaceSibling()!!
        val branchLast = branch.rbrace.getPrevNonWhitespaceSibling()!!
        if (isUsedAsExpression) {
            val canUnwrapBlock = branchFirst == branchLast && !isInsideCascadeIf
            val replaceWith = when {
                canUnwrapBlock -> branchFirst
                isInsideCascadeIf -> branch
                else -> branch.wrapAsBlockExpr(factory)
            }
            val replaced = ifExpr.replace(replaceWith)
            descriptor.findExistingEditor()?.caretModel?.moveToOffset(replaced.startOffset)
        } else {
            val ifStmt = ifExpr.parent as? RsExprStmt ?: ifExpr
            if (branchFirst != branch.rbrace) {
                ifStmt.parent.addRangeAfter(branchFirst, branchLast, ifStmt)
            }
            ifStmt.delete()
        }
    }
}

private fun RsIfExpr.isUsedAsExpression(): Boolean =
    when (val parent = parent) {
        is RsExprStmt -> false
        is RsBlock -> this != parent.expr
        else -> true
    }

private fun RsBlock.wrapAsBlockExpr(factory: RsPsiFactory): RsBlockExpr {
    val blockExpr = factory.createBlockExpr("")
    blockExpr.block.replace(this)
    return blockExpr
}
