/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil
import org.rust.RsBundle
import org.rust.ide.refactoring.*
import org.rust.ide.utils.getTopmostParentInside
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.runWriteCommandAction


fun extractExpression(
    editor: Editor,
    expr: RsExpr,
    postfixLet: Boolean,
    @Suppress("UnstableApiUsage")
    @NlsContexts.Command commandName: String
) {
    if (!expr.isValid) return
    val occurrences = findOccurrences(expr)
    showOccurrencesChooser(editor, expr, occurrences) { occurrencesToReplace ->
        ExpressionReplacer(expr.project, editor, expr)
            .replaceElementForAllExpr(occurrencesToReplace, postfixLet, commandName)
    }
}

private class ExpressionReplacer(
    private val project: Project,
    private val editor: Editor,
    private val chosenExpr: RsExpr
) {
    private val psiFactory = RsPsiFactory(project)
    private val suggestedNames = chosenExpr.suggestedNames()

    fun replaceElementForAllExpr(
        exprs: List<RsExpr>,
        postfixLet: Boolean,
        @Suppress("UnstableApiUsage")
        @NlsContexts.Command commandName: String
    ) {
        val anchor = findAnchor(exprs, chosenExpr) ?: return
        val sortedExprs = exprs.sortedBy { it.startOffset }
        val firstExpr = sortedExprs.firstOrNull() ?: chosenExpr

        // `inlinableExprStmt` is the element that should be replaced with the new let binding.
        // This is the statement surrounding the entire expression, either with or without a semicolon.
        // In the latter case we replace the expression with a `let` binding only if the expression
        // isn't returned from a block, or if were explicitly told to replace it via a postfix template.
        // Value is null if the binding should not be inlined, otherwise equal to the expression into
        // which we inline the binding.
        val inlinableExprStmt = (firstExpr.parent as? RsExprStmt?)?.takeIf { it == anchor && (!it.isTailStmt || postfixLet)}

        val let = createLet(suggestedNames.default)
        val name = psiFactory.createExpression(suggestedNames.default)

        project.runWriteCommandAction(commandName) {
            val letBinding = if (inlinableExprStmt != null) {
                // `inline let` is a statement, i.e. it returns `()`, so this replacement produces equivalent
                // code only when the replaced expression had a value coerced to `()`, i.e. it is either `expr;`,
                // or an expression with a return type of `()`. The latter case is very unlikely in practice, so
                // we use the `inline let` only in the former one. Using inline let in those cases is a simple
                // optimization of the code structure.
                val binding = inlinableExprStmt.replace(let)
                sortedExprs.drop(1).forEach { it.replace(name) }
                binding
            } else {
                val parentLambda = chosenExpr.ancestorStrict<RsLambdaExpr>()
                val lambdaMayBeAnchor = parentLambda != null && sortedExprs.all { parentLambda.isAncestorOf(it) }
                val binding = introduceLet(anchor, let)
                sortedExprs.forEach { it.replace(name) }
                if (lambdaMayBeAnchor) {
                    binding.moveIntoLambdaBlockIfNeeded(parentLambda)
                } else {
                    binding
                }
            }

            val nameElem = moveEditorToNameElement(editor, letBinding)

            if (nameElem != null) {
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                RsInPlaceVariableIntroducer(nameElem, editor, project, RsBundle.message("command.name.choose.variable"))
                    .performInplaceRefactoring(suggestedNames.all)
            }
        }
    }

    /**
     * Creates a let binding for the found expression.
     */
    private fun createLet(name: String): RsLetDecl {
        val parent = chosenExpr.parent
        // FIXME: should find which of the extracted expressions are used in an actual mutable context.
        val mutable = parent is RsUnaryExpr && parent.mut != null
        return psiFactory.createLetDeclaration(name, chosenExpr, mutable)
    }

    private fun introduceLet(anchor: PsiElement, let: RsLetDecl): PsiElement {
        val context = anchor.parent
        val newline = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n")

        val result = context.addBefore(let, anchor)
        context.addAfter(newline, result)
        return result
    }

    private fun PsiElement.moveIntoLambdaBlockIfNeeded(lambda: RsLambdaExpr?): PsiElement? {
        val body = lambda?.expr ?: return this
        if (body is RsBlockExpr) return this
        val blockExpr = body.replace(psiFactory.createBlockExpr("\n${body.text}\n")) as RsBlockExpr
        val block = blockExpr.block
        return block.addBefore(this, block.syntaxTailStmt).also { this.delete() }
    }
}

/**
 * An anchor point is surrounding element before the block scope, which is used to scope the insertion of the new let binding.
 */
private fun findAnchor(exprs: List<PsiElement>, chosenExpr: RsExpr): PsiElement? {
    val commonParent = PsiTreeUtil.findCommonParent(chosenExpr, *exprs.toTypedArray())
        ?: return null
    val firstExpr = exprs.minByOrNull { it.startOffset } ?: chosenExpr

    val block = commonParent.ancestorOrSelf<RsBlock>()
        ?: return null

    return firstExpr.getTopmostParentInside(block)
}
