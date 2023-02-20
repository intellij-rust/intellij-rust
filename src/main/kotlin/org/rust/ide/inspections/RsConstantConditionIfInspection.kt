/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.prevLeaf
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.asBool
import org.rust.lang.utils.evaluation.evaluate

/** See also [RsRedundantElseInspection]. */
class RsConstantConditionIfInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitIfExpr(ifExpr: RsIfExpr) {
                val condition = ifExpr.condition ?: return
                if (condition.expr?.descendantOfTypeOrSelf<RsLetExpr>() != null) return
                val conditionValue = condition.expr?.evaluate()?.asBool() ?: return

                val isUsedAsExpression = ifExpr.isUsedAsExpression()
                val fix = if (!conditionValue && ifExpr.elseBranch == null) {
                    val isInsideCascadeIf = ifExpr.isInsideCascadeIf
                    if (isUsedAsExpression && !isInsideCascadeIf) return
                    createDeleteElseBranchFix(ifExpr, isInsideCascadeIf)
                } else {
                    SimplifyFix(conditionValue)
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
) : LocalQuickFix {
    override fun getFamilyName(): String = name

    override fun getName(): String = "Simplify expression"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val ifExpr = descriptor.psiElement.ancestorStrict<RsIfExpr>() ?: return

        // `if false {} else if ... {} else ...`
        ifExpr.elseBranch?.ifExpr?.let { elseIfExpr ->
            if (!conditionValue) {
                ifExpr.replace(elseIfExpr)
                return
            }
        }

        val branch = (if (conditionValue) ifExpr.block else ifExpr.elseBranch?.block) ?: return
        val replaced = ifExpr.replaceWithBlockContent(branch)
        if (replaced != null) {
            descriptor.findExistingEditor()?.caretModel?.moveToOffset(replaced.startOffset)
        }
    }
}

private fun RsIfExpr.isUsedAsExpression(): Boolean = parent !is RsExprStmt

private fun RsIfExpr.replaceWithBlockContent(branch: RsBlock): PsiElement? {
    val parent = parent
    val firstStmt = branch.lbrace.getNextNonWhitespaceSibling()!!
    val lastStmt = branch.rbrace.getPrevNonWhitespaceSibling()!!
    return if (parent is RsExprStmt) {
        if (!parent.isTailStmt) {
            // fn main() {
            //     if true { 1 } else { 0 }
            //     func();
            // }
            block?.syntaxTailStmt?.addSemicolon()
        }

        val ifStmt = parent as? RsExprStmt ?: this as RsElement
        if (firstStmt != branch.rbrace) {
            ifStmt.parent.addRangeAfter(firstStmt, lastStmt, ifStmt)
        }
        ifStmt.delete()
        return null
    } else {
        val replaceWith = when {
            isInsideCascadeIf -> branch
            firstStmt == lastStmt && firstStmt is RsExprStmt && !firstStmt.hasSemicolon -> firstStmt.expr
            else -> branch.wrapAsBlockExpr(RsPsiFactory(project))
        }
        replace(replaceWith)
    }
}

private val RsIfExpr.isInsideCascadeIf get() = parent is RsElseBranch

private fun RsBlock.wrapAsBlockExpr(factory: RsPsiFactory): RsBlockExpr {
    val blockExpr = factory.createBlockExpr("")
    blockExpr.block.replace(this)
    return blockExpr
}
