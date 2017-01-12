package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.rightSiblings

/**
 * Checks for potentially missing `else`s.
 * A partial analogue of Clippy's suspicious_else_formatting.
 * QuickFix: Change to `else if`
 */
class RustMissingElseInspection : RustLocalInspectionTool() {
    override fun getDisplayName() = "Missing else"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitExprStmt(expr: RsExprStmt) {
                val firstIf = expr.extractIf() ?: return
                val nextIf = expr.rightSiblings
                    .dropWhile { (it is PsiWhiteSpace || it is PsiComment) && '\n' !in it.text }
                    .firstOrNull()
                    .extractIf() ?: return
                val condition = nextIf.condition ?: return
                val rangeStart = expr.startOffsetInParent + firstIf.textLength
                val rangeLen = condition.expr.textRange.startOffset - firstIf.textRange.startOffset - firstIf.textLength
                val fixRange = TextRange(nextIf.textRange.startOffset, nextIf.textRange.startOffset)
                holder.registerProblem(
                    expr.parent,
                    TextRange(rangeStart, rangeStart + rangeLen),
                    "Suspicious if. Did you mean `else if`?",
                    SubstituteTextFix(expr.containingFile, fixRange, "else ", "Change to `else if`"))
            }
        }

    private fun PsiElement?.extractIf(): RsIfExpr? = when (this) {
        is RsIfExpr -> this
        is RsExprStmt -> firstChild.extractIf()
        else -> null
    }
}
