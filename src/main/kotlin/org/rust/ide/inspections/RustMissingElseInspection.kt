package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustExprStmtElement
import org.rust.lang.core.psi.RustIfExprElement

/**
 * Checks for potentially missing `else`s.
 * A partial analogue of Clippy's suspicious_else_formatting.
 * QuickFix: Change to `else if`
 */
class RustMissingElseInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Missing else"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RustElementVisitor() {
            override fun visitExprStmt(expr: RustExprStmtElement) {
                if (expr.extractIf() == null) return
                val nextPair = expr.nextSibling.consumeSpaces() ?: return
                val spaceLen = nextPair.second
                val nextEl = nextPair.first
                val nextIf = nextEl.extractIf() ?: return
                val rangeStart = expr.startOffsetInParent + expr.textLength
                val range = TextRange(rangeStart, rangeStart + spaceLen + nextIf.expr.startOffsetInParent)
                val fixRange = TextRange(nextIf.textRange.startOffset, nextIf.textRange.startOffset)
                holder.registerProblem(
                    expr.parent,
                    range,
                    "Suspicious if. Did you mean `else if`?",
                    SubstituteTextFix(expr.containingFile, fixRange, "else ", "Change to `else if`"))
            }
        }

    private fun PsiElement?.extractIf(): RustIfExprElement? = when(this) {
        is RustIfExprElement -> this
        is RustExprStmtElement -> firstChild.extractIf()
        else -> null
    }

    /**
     * Finds the first non-space/comment sibling starting from this element.
     * Returns the found sibling along with the length of the consumed spaces/comments.
     * If there's a space/comment with a line break, returns `null`.
     */
    private fun PsiElement?.consumeSpaces(): Pair<PsiElement?, Int>? {
        var nextEl = this
        var len = 0
        while (nextEl is PsiWhiteSpace || nextEl is PsiComment) {
            if (nextEl.textContains('\n')) {
                return null
            }
            len += nextEl.textLength
            nextEl = nextEl.nextSibling
        }
        return Pair(nextEl, len)
    }
}
