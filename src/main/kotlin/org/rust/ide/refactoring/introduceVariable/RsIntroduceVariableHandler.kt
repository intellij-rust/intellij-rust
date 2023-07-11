/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.introduceVariable

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.RsBundle
import org.rust.ide.refactoring.findCandidateExpressionsToExtract
import org.rust.ide.refactoring.showExpressionChooser
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.Testmark

class RsIntroduceVariableHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is RsFile) return
        val exprs = findCandidateExpressionsToExtract(editor, file)

        if (exprs.isEmpty()) {
            val message = RefactoringBundle.message(
                if (editor.selectionModel.hasSelection())
                    "selected.block.should.represent.an.expression"
                else
                    "refactoring.introduce.selection.error"
            )
            val title = RefactoringBundle.message("introduce.variable.title")
            val helpId = "refactoring.extractVariable"
            CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)
        } else {
            val extractor = { expr: RsExpr ->
                extractExpression(
                    editor, expr, postfixLet = false, RsBundle.message("command.name.introduce.local.variable")
                )
            }
            if (exprs.size == 1) {
                extractor(exprs.single())
            } else showExpressionChooser(editor, exprs) {
                extractor(it)
            }
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called from the editor.
    }
}

object IntroduceVariableTestmarks {
    object InvalidNamePart : Testmark()
}
