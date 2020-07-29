/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.introduceConstant

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.ide.refactoring.*
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.openapiext.runWriteCommandAction

class RsIntroduceConstantHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is RsFile) return
        val exprs = findCandidateExpressionsToExtract(editor, file).filter { it.isExtractable() }

        when (exprs.size) {
            0 -> {
                val message = RefactoringBundle.message(if (editor.selectionModel.hasSelection())
                    "selected.block.should.represent.an.expression"
                else
                    "refactoring.introduce.selection.error"
                )
                val title = RefactoringBundle.message("introduce.constant.title")
                val helpId = "refactoring.extractConstant"
                CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)
            }
            1 -> extractExpression(editor, exprs.single())
            else -> {
                showExpressionChooser(editor, exprs) {
                    extractExpression(editor, it)
                }
            }
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called from the editor.
    }
}

private fun RsExpr.isExtractable(): Boolean {
    return when (this) {
        is RsLitExpr -> true
        is RsBinaryExpr -> this.left.isExtractable() && (this.right?.isExtractable() ?: true)
        else -> false
    }
}

private fun replaceWithConstant(expr: RsExpr, occurrences: List<RsExpr>, candidate: InsertionCandidate, editor: Editor) {
    val project = expr.project
    val factory = RsPsiFactory(project)
    val suggestedNames = expr.suggestedNames()
    val name = suggestedNames.default.toUpperCase()
    val const = factory.createConstant(name, expr)

    project.runWriteCommandAction {
        val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
        val context = candidate.parent
        val insertedConstant = context.addBefore(const, candidate.anchor) as RsConstant
        context.addAfter(newline, insertedConstant)
        val replaced = occurrences.map {
            val created = factory.createExpression(name)
            val element = it.replace(created) as RsPathExpr
            if (element.path.reference?.resolve() == null) {
                RsImportHelper.importElements(element, setOf(insertedConstant))
            }
            element
        }

        editor.caretModel.moveToOffset(insertedConstant.identifier?.textRange?.startOffset
            ?: error("Impossible because we just created a constant with a name"))

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        RsInPlaceVariableIntroducer(insertedConstant, editor, project, "Choose a constant name", replaced)
            .performInplaceRefactoring(LinkedHashSet(suggestedNames.all.map { it.toUpperCase() }))
    }
}

private fun extractExpression(editor: Editor, expr: RsExpr) {
    if (!expr.isValid) return
    val occurrences = findOccurrences(expr)
    showOccurrencesChooser(editor, expr, occurrences) { occurrencesToReplace ->
        showInsertionChooser(editor, expr) {
            replaceWithConstant(expr, occurrencesToReplace, it, editor)
        }
    }
}
