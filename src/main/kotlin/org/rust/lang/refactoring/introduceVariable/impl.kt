/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.introduceVariable

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer
import org.rust.ide.utils.findExpressionAtCaret
import org.rust.ide.utils.findExpressionInRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestors
import org.rust.openapiext.runWriteCommandAction
import java.util.*


fun findCandidateExpressionsToExtract(editor: Editor, file: RsFile): List<RsExpr> {
    val selection = editor.selectionModel
    return if (selection.hasSelection()) {
        // If there's an explicit selection, suggest only one expression
        listOfNotNull(findExpressionInRange(file, selection.selectionStart, selection.selectionEnd))
    } else {
        val expr = findExpressionAtCaret(file, editor.caretModel.offset)
            ?: return emptyList()
        // Finds possible expressions that might want to be bound to a local variable.
        // We don't go further than the current block scope,
        // further more path expressions don't make sense to bind to a local variable so we exclude them.
        expr.ancestors
            .takeWhile { it !is RsBlock }
            .filterIsInstance<RsExpr>()
            .filter { it !is RsPathExpr }
            .toList()
    }
}

fun extractExpression(editor: Editor, expr: RsExpr) {
    if (!expr.isValid) return
    val occurrences = findOccurrences(expr)
    showOccurrencesChooser(editor, expr, occurrences) { occurrencesToReplace ->
        replaceExpression(editor, expr, occurrencesToReplace)
    }
}


/**
 * Replaces an element in two different cases.
 *
 * Either we need to put a let in front of a statement on the same line.
 * Or we extract an expression and put that in a let on the line above.
 */
private fun replaceExpression(editor: Editor, chosenExpr: RsExpr, exprs: List<PsiElement>) {
    val anchor = findAnchor(chosenExpr)
    val parent = chosenExpr.parent
    val project = chosenExpr.project

    val replacer = ExpressionReplacer(project, editor, chosenExpr)
    when {
        anchor == chosenExpr -> replacer.inlineLet(project, editor, chosenExpr, chosenExpr)
        parent is RsExprStmt -> replacer.inlineLet(project, editor, chosenExpr, chosenExpr.parent)
        else -> replacer.replaceElementForAllExpr(exprs)
    }
}


private class ExpressionReplacer(
    private val project: Project,
    private val editor: Editor,
    private val chosenExpr: RsExpr
) {
    private val psiFactory = RsPsiFactory(project)

    /**
     * @param expr the expression we are creating a let binding for and which to suggest names for.
     * @param elementToReplace the element that should be replaced with the new let binding.
     *         this can be either the expression its self if it had no semicolon at the end.
     *         or the statement surrounding the entire expression if it already had a semicolon.
     */
    fun inlineLet(project: Project, editor: Editor, expr: RsExpr, elementToReplace: PsiElement) {
        val suggestedNames = expr.suggestedNames()
        val nameElem: RsPatBinding? = project.runWriteCommandAction {
            val statement = psiFactory.createLetDeclaration(suggestedNames.default, expr)
            val newStatement = elementToReplace.replace(statement)
            moveEditorToNameElement(newStatement)
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        if (nameElem != null) {
            RustInPlaceVariableIntroducer(nameElem, editor, project, "choose a variable", emptyArray())
                .performInplaceRefactoring(suggestedNames.all)
        }
    }


    fun replaceElementForAllExpr(exprs: List<PsiElement>) {
        val anchor = findAnchor(exprs.minBy { it.textRange.startOffset } ?: chosenExpr)
            ?: return

        val suggestedNames = chosenExpr.suggestedNames()
        val (let, name) = createLet(suggestedNames.default) ?: return

        val nameElem: RsPatBinding? = project.runWriteCommandAction {
            val newElement = introduceLet(project, anchor, let)
            exprs.forEach { it.replace(name) }
            moveEditorToNameElement(newElement)
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        if (nameElem != null) {
            RustInPlaceVariableIntroducer(nameElem, editor, project, "choose a variable", emptyArray())
                .performInplaceRefactoring(suggestedNames.all)
        }
    }

    /**
     * Creates a let binding for the found expression.
     * Returning handles to the complete let expr and the identifier inside the newly created let binding.
     */
    private fun createLet(name: String): Pair<RsLetDecl, PsiElement>? {
        val parent = chosenExpr.parent

        val mutable = parent is RsUnaryExpr && parent.mut != null
        val let = psiFactory.createLetDeclaration(name, chosenExpr, mutable = mutable)

        val binding = let.findBinding()
            ?: error("Failed to create a proper let expression: `${let.text}`")

        return let to binding.identifier
    }

    private fun introduceLet(project: Project, anchor: PsiElement, let: RsLetDecl): PsiElement? {
        val context = anchor.parent
        val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")

        return context?.addBefore(let, context.addBefore(newline, anchor))
    }

    private fun moveEditorToNameElement(element: PsiElement?): RsPatBinding? {
        val newName = element?.findBinding()

        editor.caretModel.moveToOffset(newName?.identifier?.textRange?.startOffset ?: 0)

        return newName
    }
}

/**
 * An anchor point is surrounding element before the block scope, which is used to scope the insertion of the new let binding.
 */
private fun findAnchor(expr: PsiElement): PsiElement? {
    val block = expr.ancestorOrSelf<RsBlock>()
        ?: return null

    var anchor = expr
    while (anchor.parent != block) {
        anchor = anchor.parent
    }

    return anchor
}

/**
 * Finds occurrences in the sub scope of expr, so that all will be replaced if replace all is selected.
 */
private fun findOccurrences(expr: RsExpr): List<RsExpr> {
    val visitor = object : PsiRecursiveElementVisitor() {
        val foundOccurrences = ArrayList<RsExpr>()

        override fun visitElement(element: PsiElement) {
            if (element is RsExpr && PsiEquivalenceUtil.areElementsEquivalent(expr, element)) {
                foundOccurrences.add(element)
            } else {
                super.visitElement(element)
            }
        }
    }

    val block = expr.ancestorOrSelf<RsBlock>() ?: return emptyList()
    block.acceptChildren(visitor)
    return visitor.foundOccurrences
}

private fun PsiElement.findBinding() = PsiTreeUtil.findChildOfType(this, RsPatBinding::class.java)

private class RustInPlaceVariableIntroducer(
    elementToRename: PsiNamedElement,
    editor: Editor,
    project: Project,
    title: String,
    occurrences: Array<PsiElement>
) : InplaceVariableIntroducer<PsiElement>(elementToRename, editor, project, title, occurrences, null)
