package org.rust.lang.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Conditions
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.util.containers.JBIterable
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprStmtElement

class RustLocalVariableHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        val offSet = editor?.caretModel?.offset
        val expr = PsiTreeUtil.getNonStrictParentOfType(file?.findElementAt(offSet ?: 0), RustExprStmtElement::class.java)

        WriteCommandAction.runWriteCommandAction(project) {
            val statement = RustElementFactory.createVariableDeclaration(project, "x", expr!!)
            expr.replace(statement!!)
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor?.document!!)
        println(expr)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        println("not from the editor.")
    }
}
