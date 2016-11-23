package org.rust.lang.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.refactoring.RefactoringActionHandler
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprStmtElement

class RustLocalVariableHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        val offSet = editor?.caretModel?.offset
        val expr = PsiTreeUtil.getNonStrictParentOfType(file?.findElementAt(offSet ?: 0), RustExprStmtElement::class.java)
        val exprs: List<RustExprStmtElement> = possibleExpressions(expr!!)

        val passer: Pass<RustExprStmtElement> = object : Pass<RustExprStmtElement>() {
            override fun pass(t: RustExprStmtElement?) {
                if (t != null && t.isValid) {

                }
            }
        }

        IntroduceTargetChooser.showChooser(editor!!, exprs, passer) {
            it.text
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val statement = RustElementFactory.createVariableDeclaration(project, "x", expr!!)
            expr.replace(statement!!)
        }

        println(expr)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        println("not from the editor.")
    }

    fun possibleExpressions(expr: RustExprStmtElement) = SyntaxTraverser.psiApi().parents(expr)
        .takeWhile { it !is RustBlockElement }
        .filterIsInstance(RustExprStmtElement::class.java)


}
