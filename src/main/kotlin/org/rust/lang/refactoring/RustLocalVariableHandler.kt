package org.rust.lang.refactoring

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.IntroduceTargetChooser
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import org.rust.lang.core.psi.*
import java.util.*

class RustLocalVariableHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        val offSet = editor?.caretModel?.offset
        val expr = PsiTreeUtil.getNonStrictParentOfType(file?.findElementAt(offSet ?: 0), RustExprElement::class.java)
        val exprs: List<RustExprElement> = expr?.let(::possibleExpressions) ?: emptyList()

        val passer: Pass<RustExprElement> = pass {
            if (expr != null && expr.isValid) {
                val occurrences = findOccurrences(expr)

                OccurrencesChooser.simpleChooser<PsiElement>(editor).showChooser(expr, occurrences, pass { choice ->
                    if (choice == OccurrencesChooser.ReplaceChoice.ALL) {
                        replaceElementForAllExpr(project, occurrences)
                    } else {
                        replaceElement(expr, project)
                    }
                })
            }
        }

        IntroduceTargetChooser.showChooser(editor!!, exprs, passer) {
            it.text
        }
    }

    private fun replaceElement(expr: RustExprElement, project: Project) {
        //the expr that has been chosen
        val parent = expr.parent
        if (parent is RustExprStmtElement) {
            replaceElementForStmt(project, parent)
        } else {
            replaceElementForExpr(project, expr)
        }
    }

    fun findExpr(file: PsiFile?, offSet: Int) = PsiTreeUtil.getNonStrictParentOfType(file?.findElementAt(offSet), RustExprElement::class.java)

    fun replaceElementForExpr(project: Project, expr: RustExprElement) {
        WriteCommandAction.runWriteCommandAction(project) {
            val anchor = findAnchor(expr)!!
            val context = anchor.context
            val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(System.lineSeparator())

            val statement = RustElementFactory.createVariableDeclaration(project, "x", expr)!!
            context?.addBefore(statement, context.addBefore(newline, anchor))

            val id = PsiTreeUtil.findChildOfType(statement, RustPatElement::class.java)!!
            expr.replace(id)
        }
    }

    fun replaceElementForAllExpr(project: Project, exprs: List<PsiElement>) {
        WriteCommandAction.runWriteCommandAction(project) {
            val id = introduceLet(exprs.first(), project)
            exprs.forEach { it.replace(id) }
        }
    }

    private fun introduceLet(expr: PsiElement, project: Project): RustPatElement {
        val anchor = findAnchor(expr)!!
        val context = anchor.context
        val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(System.lineSeparator())

        val statement = RustElementFactory.createVariableDeclaration(project, "x", expr)!!
        context?.addBefore(statement, context.addBefore(newline, anchor))

        return PsiTreeUtil.findChildOfType(statement, RustPatElement::class.java)!!
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

    fun foo(n: Int) = n
}

fun findAnchor(expr: PsiElement) = PsiTreeUtil.getNonStrictParentOfType(expr, RustExprStmtElement::class.java)

fun possibleExpressions(expr: RustExprElement) = SyntaxTraverser.psiApi().parents(expr)
    .takeWhile { it !is RustBlockElement }
    .filterIsInstance(RustExprElement::class.java)

fun findBlock(expr: PsiElement) = PsiTreeUtil.getNonStrictParentOfType(expr, RustBlockElement::class.java)

fun findOccurrences(expr: PsiElement): List<PsiElement> {
    val visitor = OccurrenceVisitor(expr)

    val occurrences: List<PsiElement> = findBlock(expr)?.let {
        it.acceptChildren(visitor)
        visitor.foundOccurrences
    } ?: emptyList()

    return occurrences
}

fun <T> pass(pass: (T) -> Unit): Pass<T> {
    return object : Pass<T>() {
        override fun pass(t: T) {
            pass.invoke(t)
        }
    }
}


class OccurrenceVisitor(val element: PsiElement) : PsiRecursiveElementVisitor() {
    val foundOccurrences = ArrayList<PsiElement>()


    override fun visitElement(element: PsiElement?) {
        if (element != null) {
            if (PsiEquivalenceUtil.areElementsEquivalent(this.element, element)) {
                foundOccurrences.add(element)
            } else {
                super.visitElement(element)
            }
        }
    }
}
