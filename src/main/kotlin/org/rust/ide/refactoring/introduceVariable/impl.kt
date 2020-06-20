/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.refactoring.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.runWriteCommandAction


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
        project.runWriteCommandAction {
            val statement = psiFactory.createLetDeclaration(suggestedNames.default, expr)
            val newStatement = elementToReplace.replace(statement)
            val nameElem = moveEditorToNameElement(editor, newStatement)

            if (nameElem != null) {
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                RsInPlaceVariableIntroducer(nameElem, editor, project, "choose a variable")
                    .performInplaceRefactoring(suggestedNames.all)
            }
        }
    }


    fun replaceElementForAllExpr(exprs: List<PsiElement>) {
        val anchor = findAnchor(exprs, chosenExpr) ?: return

        val suggestedNames = chosenExpr.suggestedNames()
        val let = createLet(suggestedNames.default)
        val name = psiFactory.createExpression(suggestedNames.default)

        project.runWriteCommandAction {
            val newElement = introduceLet(project, anchor, let)
            exprs.forEach { it.replace(name) }
            val nameElem = moveEditorToNameElement(editor, newElement)

            if (nameElem != null) {
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                RsInPlaceVariableIntroducer(nameElem, editor, project, "choose a variable")
                    .performInplaceRefactoring(suggestedNames.all)
            }
        }
    }

    /**
     * Creates a let binding for the found expression.
     */
    private fun createLet(name: String): RsLetDecl {
        val parent = chosenExpr.parent
        val mutable = parent is RsUnaryExpr && parent.mut != null
        return psiFactory.createLetDeclaration(name, chosenExpr, mutable)
    }

    private fun introduceLet(project: Project, anchor: PsiElement, let: RsLetDecl): PsiElement? {
        val context = anchor.parent
        val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")

        val result = context.addBefore(let, anchor)
        context.addAfter(newline, result)
        return result
    }
}

/**
 * An anchor point is surrounding element before the block scope, which is used to scope the insertion of the new let binding.
 */
private fun findAnchor(expr: PsiElement): PsiElement? {
    return findAnchor(expr, expr)
}

private fun findAnchor(exprs: List<PsiElement>, chosenExpr: RsExpr): PsiElement? {
    val commonParent = PsiTreeUtil.findCommonParent(chosenExpr, *exprs.toTypedArray())
        ?: return null
    val firstExpr = exprs.minBy { it.startOffset } ?: chosenExpr
    return findAnchor(commonParent, firstExpr)
}

private fun findAnchor(commonParent: PsiElement, firstExpr: PsiElement): PsiElement? {
    val block = commonParent.ancestorOrSelf<RsBlock>()
        ?: return null

    var anchor = firstExpr
    while (anchor.parent != block) {
        anchor = anchor.parent
    }

    return anchor
}
