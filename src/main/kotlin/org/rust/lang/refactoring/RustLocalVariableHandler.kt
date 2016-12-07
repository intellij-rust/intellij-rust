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
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.core.psi.util.findExpressionInRange
import org.rust.lang.core.psi.util.parentOfType
import java.util.*

/**
 * Introduce variable refactoring entry point.
 *
 * [RustLocalVariableHandler] handles all the asynchronous user interaction, while
 * [RustIntroduceVariableRefactoring] does actual computations.
 */
class RustLocalVariableHandler : RefactoringActionHandler {

    /**
     * Entry point for the ui, can't be called in unit tests.
     */
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        if (file !is RustFile) return
        val refactoring = RustIntroduceVariableRefactoring(project, editor, file)
        val exprs = refactoring.possibleTargets()

        fun extractExpression(expr: RustExprElement) {
            if (!expr.isValid) return
            val occurrences = findOccurrences(expr)
            OccurrencesChooser.simpleChooser<PsiElement>(editor).showChooser(expr, occurrences, pass { choice ->
                val toReplace = if (choice == OccurrencesChooser.ReplaceChoice.ALL) occurrences else listOf(expr)
                refactoring.replaceElement(toReplace)
            })
        }

        when (exprs.size) {
            0 -> {
                val message = RefactoringBundle.message(if (editor.selectionModel.hasSelection())
                    "selected.block.should.represent.an.expression"
                else
                    "refactoring.introduce.selection.error"
                )
                val title = RefactoringBundle.message("introduce.variable.title")
                val helpId = "refactoring.extractVariable"
                CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId)
            }
            1 -> extractExpression(exprs.single())
            else -> IntroduceTargetChooser.showChooser(editor, exprs, pass(::extractExpression), { it.text })
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called form the editor.
    }
}

class RustIntroduceVariableRefactoring(
    private val project: Project,
    private val editor: Editor,
    private val file: RustFile
) {

    private val psiFactory = RustPsiFactory(project)

    fun possibleTargets(): List<RustExprElement> {
        val selection = editor.selectionModel
        return if (selection.hasSelection()) {
            // If there's an explicit selection, suggest only one expression
            listOfNotNull(findExpressionInRange(file, selection.selectionStart, selection.selectionEnd))
        } else {
            val offset = editor.caretModel.offset
            val elementAfterCaret = file.findElementAt(offset)
            val elementBeforeCaret = file.findElementAt(offset - 1)
            val expr = elementAfterCaret?.parentOfType<RustExprElement>(strict = false)
                ?: elementBeforeCaret?.parentOfType<RustExprElement>(strict = false)
                ?: return emptyList()

            // Finds possible expressions that might want to be bound to a local variable.
            // We don't go further than the current block scope,
            // further more path expressions don't make sense to bind to a local variable so we exclude them.
            expr.ancestors
                .takeWhile { it !is RustBlockElement }
                .filterIsInstance<RustExprElement>()
                .filter { it !is RustPathExprElement }
                .toList()
        }
    }

    /**
     * Replaces an element in two different cases.
     *
     * Either we need to put a let in front of a statement on the same line.
     * Or we extract an expression and put that in a let on the line above.
     */
    fun replaceElement(exprs: List<PsiElement>) {
        //the expr that has been chosen
        val expr = exprs.first()
        val anchor = findAnchor(expr)
        val parent = expr.parent

        when {
            anchor == expr -> inlineLet(project, editor, anchor, { psiFactory.createLetDeclaration("x", it) })
            parent is RustExprStmtElement -> inlineLet(project, editor, parent, { psiFactory.createLetDeclaration("x", it) })
            else -> replaceElementForAllExpr(exprs)
        }
    }

    fun replaceElementForAllExpr(exprs: List<PsiElement>) {
        val expr = exprs.first()

        val (let, name) = createLet(expr)
            ?: return

        var nameElem: RustPatBindingElement? = null

        WriteCommandAction.runWriteCommandAction(project) {
            val newElement = introduceLet(project, expr, let)
            exprs.forEach { it.replace(name) }
            nameElem = moveEditorToNameElement(editor, newElement)
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        nameElem?.let { RustInPlaceVariableIntroducer(it, editor, project, "choose a variable", emptyArray()).performInplaceRefactoring(LinkedHashSet()) }
    }

    /**
     * Creates a let binding for the found expression.
     * Returning handles to the complete let expr and the identifier inside the newly created let binding.
     */
    private fun createLet(expr: PsiElement): Pair<RustLetDeclElement, PsiElement>? {
        val parent = expr.parent

        val mutable = parent is RustUnaryExprElement && parent.mut != null
        val let = psiFactory.createLetDeclaration("x", expr, mutable = mutable)

        val binding = let.findBinding()
            ?: error("Faild to create a proper let expression: `${let.text}`")

        return let to binding.identifier
    }

    private fun introduceLet(project: Project, expr: PsiElement, let: RustLetDeclElement): PsiElement? {
        val anchor = findAnchor(expr)
        val context = anchor?.context
        val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")

        return context?.addBefore(let, context.addBefore(newline, anchor))
    }

    private fun <T : PsiElement> inlineLet(project: Project, editor: Editor, stmt: T, statementFactory: (T) -> RustStmtElement) {
        var newNameElem: RustPatBindingElement? = null
        WriteCommandAction.runWriteCommandAction(project) {
            val statement = statementFactory.invoke(stmt)
            val newStatement = stmt.replace(statement)

            newNameElem = moveEditorToNameElement(editor, newStatement)
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        newNameElem?.let { RustInPlaceVariableIntroducer(it, editor, project, "choose a variable", emptyArray()).performInplaceRefactoring(LinkedHashSet()) }
    }

    private fun moveEditorToNameElement(editor: Editor, element: PsiElement?): RustPatBindingElement? {
        val newName = element?.findBinding()

        editor.caretModel.moveToOffset(newName?.identifier?.textRange?.startOffset ?: 0)

        return newName
    }
}

/**
 * An anchor point is surrounding element before the block scope, which is used to scope the insertion of the new let binding.
 */
private fun findAnchor(expr: PsiElement): PsiElement? {
    val block = expr.parentOfType<RustBlockElement>(strict = false)
        ?: return null

    var anchor = expr
    while (anchor.parent != block) {
        anchor = anchor.parent
    }

    return anchor
}

private fun findBlock(expr: PsiElement) = PsiTreeUtil.getNonStrictParentOfType(expr, RustBlockElement::class.java)

/**
 * Finds occurrences in the sub scope of expr, so that all will be replaced if replace all is selected.
 */
fun findOccurrences(expr: PsiElement): List<PsiElement> {
    val visitor = object : PsiRecursiveElementVisitor() {
        val foundOccurrences = ArrayList<PsiElement>()

        override fun visitElement(element: PsiElement) {
            if (PsiEquivalenceUtil.areElementsEquivalent(expr, element)) {
                foundOccurrences.add(element)
            } else {
                super.visitElement(element)
            }
        }
    }

    val block = findBlock(expr) ?: return emptyList()
    block.acceptChildren(visitor)
    return visitor.foundOccurrences
}

private fun <T> pass(pass: (T) -> Unit): Pass<T> {
    return object : Pass<T>() {
        override fun pass(t: T) {
            pass.invoke(t)
        }
    }
}

private fun PsiElement.findBinding() = PsiTreeUtil.findChildOfType(this, RustPatBindingElement::class.java)

private class RustInPlaceVariableIntroducer(
    elementToRename: PsiNamedElement,
    editor: Editor,
    project: Project,
    title: String,
    occurrences: Array<PsiElement>
) : InplaceVariableIntroducer<PsiElement>(elementToRename, editor, project, title, occurrences, null)
