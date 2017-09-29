/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RsElseBranch
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsVisitor

/**
 * Detects `else if` statements broken by new lines. A partial analogue
 * of the Clippy's suspicious_else_formatting lint.
 * Quick fix 1: Remove `else`
 * Quick fix 2: Join `else if`
 */
class RsDanglingElseInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Dangling else"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitElseBranch(expr: RsElseBranch) {
                val elseEl = expr.`else`
                val breakEl = elseEl.rightSiblings
                    .dropWhile { (it is PsiWhiteSpace || it is PsiComment) && '\n' !in it.text }
                    .firstOrNull() ?: return
                val ifEl = breakEl.rightSiblings
                    .dropWhile { it !is RsIfExpr }
                    .firstOrNull() ?: return
                holder.registerProblem(
                    expr,
                    TextRange(0, ifEl.startOffsetInParent + 2),
                    "Suspicious `else if` formatting",
                    SubstituteTextFix.delete(
                        "Remove `else`",
                        expr.containingFile,
                        elseEl.rangeWithPrevSpace(expr.prevSibling)
                    ),
                    SubstituteTextFix.replace(
                        "Join `else if`",
                        expr.containingFile,
                        TextRange(elseEl.textRange.endOffset, ifEl.textRange.startOffset),
                        " "
                    ))
            }
        }

    private fun PsiElement.rangeWithPrevSpace(prev: PsiElement?) = when (prev) {
        is PsiWhiteSpace -> textRange.union(prev.textRange)
        else -> textRange
    }

    private val PsiElement.rightSiblings: Sequence<PsiElement>
        get() = generateSequence(this.nextSibling) { it.nextSibling }
}
