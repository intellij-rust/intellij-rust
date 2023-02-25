/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsEmptyStmt
import org.rust.lang.core.psi.RsStmt
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.endOffsetInParent

private class FixRedundantSemicolons(start: PsiElement, end: PsiElement = start)
    : LocalQuickFixAndIntentionActionOnPsiElement(start, end) {

    override fun getFamilyName() = RsBundle.message("inspection.RedundantSemicolons.fix.name")
    override fun getText() = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val parent = startElement.parent as? RsBlock ?: return
        parent.deleteChildRange(startElement, endElement)
    }
}

/** Analogue of https://doc.rust-lang.org/rustc/lints/listing/warn-by-default.html#redundant-semicolons */
class RsRedundantSemicolonsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.RedundantSemicolons

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitBlock(block: RsBlock) {
            super.visitBlock(block)

            fun tryRegisterProblemAndClearSeq(stmts: MutableList<RsStmt>) {
                if (stmts.isEmpty()) return
                val highlighting = RsLintHighlightingType.UNUSED_SYMBOL
                if (stmts.size == 1) {
                    val description = RsBundle.message("inspection.RedundantSemicolons.description.single")
                    val fixes = listOf(FixRedundantSemicolons(stmts.first()))
                    holder.registerLintProblem(stmts.first(), description, highlighting, fixes)
                } else {
                    val description = RsBundle.message("inspection.RedundantSemicolons.description.multiple")
                    val range = TextRange.create(stmts.first().startOffsetInParent, stmts.last().endOffsetInParent)
                    val fixes = listOf(FixRedundantSemicolons(stmts.first(), stmts.last()))
                    holder.registerLintProblem(block, description, range, highlighting, fixes)
                }
                stmts.clear()
            }

            val seq = mutableListOf<RsStmt>()
            for (element in block.children) {
                when (element) {
                    is RsEmptyStmt -> seq += element
                    is RsItemElement, is RsStmt -> tryRegisterProblemAndClearSeq(seq)
                }
            }
            tryRegisterProblemAndClearSeq(seq)
        }
    }
}
