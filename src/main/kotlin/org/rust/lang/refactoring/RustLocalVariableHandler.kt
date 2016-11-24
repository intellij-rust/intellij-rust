package org.rust.lang.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.refactoring.RefactoringActionHandler
import org.rust.lang.core.psi.*

class RustLocalVariableHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        val offSet = editor?.caretModel?.offset
        val expr = PsiTreeUtil.getNonStrictParentOfType(file?.findElementAt(offSet ?: 0), RustExprElement::class.java)
        val exprs: List<RustExprElement> = expr?.let(::possibleExpressions) ?: emptyList()

        println(exprs)

        val passer: Pass<RustExprElement> = object : Pass<RustExprElement>() {
            override fun pass(t: RustExprElement?) {
                if (t != null && t.isValid) {

                }
            }
        }

        IntroduceTargetChooser.showChooser(editor!!, exprs, passer) {
            it.text
        }

        println(expr)
    }

    fun findExpr(file: PsiFile?, offSet: Int) = PsiTreeUtil.getNonStrictParentOfType(file?.findElementAt(offSet), RustExprElement::class.java)

    fun replaceElementForExpr(project: Project, expr: RustExprElement) {
        WriteCommandAction.runWriteCommandAction(project) {
            val anchor = findAnchor(expr)!!
            val context = anchor.context
            val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")

            val statement = RustElementFactory.createVariableDeclaration(project, "x", expr)!!
            context?.addBefore(statement, context.addBefore(newline, anchor))

            val id = PsiTreeUtil.findChildOfType(statement, RustPatElement::class.java)!!
            expr.replace(id)
        }
    }

    fun replaceElementForStmt(project: Project, stmt: RustExprStmtElement) {
        WriteCommandAction.runWriteCommandAction(project) {
            val statement = RustElementFactory.createVariableDeclarationFromStmt(project, "x", stmt)!!
            stmt.replace(statement)
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        println("not from the editor.")
    }
}

fun findAnchor(expr: RustExprElement) = PsiTreeUtil.getNonStrictParentOfType(expr, RustExprStmtElement::class.java)

fun possibleExpressions(expr: RustExprElement) = SyntaxTraverser.psiApi().parents(expr)
    .takeWhile { it !is RustBlockElement }
    .filterIsInstance(RustExprElement::class.java)
