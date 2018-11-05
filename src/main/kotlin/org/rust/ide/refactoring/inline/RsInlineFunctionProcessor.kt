/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inline

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.cfg.ExitPoint
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineFunctionProcessor(
    private val project: Project,
    private val function: RsFunction,
    private val ref: RsReference?,
    private val inlineThisOnly: Boolean,
    private val removeDefinition: Boolean,
    private val factory: RsPsiFactory = RsPsiFactory(project)
) : BaseRefactoringProcessor(project) {

    override fun findUsages(): Array<UsageInfo> {
        if (inlineThisOnly && ref != null) {
            return arrayOf(UsageInfo(ref))
        }

        val projectScope = GlobalSearchScope.projectScope(project)
        val usages = mutableListOf<PsiReference>()
        usages.addAll(ReferencesSearch.search(function, projectScope).findAll())

        val usagesAsReference = usages.filter {
            it.element.ancestorOrSelf<RsCallExpr>() == null
                && it.element.ancestorOrSelf<RsMethodCall>() == null
        }

        if (usagesAsReference.isNotEmpty() && removeDefinition) {
            throw IllegalArgumentException(
                "Cannot remove function definition: function pointer inline is not supported")
        }

        usages.removeAll(usagesAsReference)
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        usages.asIterable().forEach loop@{
            val reference = it.reference as? RsReference ?: return@loop
            inlineWithLetBindingsAdded(reference, function)
        }
        if (removeDefinition) {
            function.delete()
        }
    }

    override fun getCommandName(): String = "Inline function ${function.declaration}"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return object : UsageViewDescriptor {
            override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) =
                RefactoringBundle.message("comments.elements.header",
                    UsageViewBundle.getOccurencesString(usagesCount, filesCount))

            override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
                RefactoringBundle.message("invocations.to.be.inlined",
                    UsageViewBundle.getReferencesString(usagesCount, filesCount))

            override fun getElements() = arrayOf(function)

            override fun getProcessedElementsHeader() = "Function to inline"
        }
    }

    companion object {
        fun doesFunctionHaveMultipleReturns(fn: RsFunction): Boolean {
            var entryCount = 0
            val sink: (ExitPoint) -> Unit = {
                if (it !is ExitPoint.TryExpr) {
                    ++entryCount
                }
            }
            ExitPoint.process(fn, sink)
            return entryCount > 1
        }

        fun isFunctionRecursive(fn: RsFunction): Boolean {
            return fn.descendantsOfType<RsPath>().any { it.reference?.resolve() == fn }
        }

        fun checkIfLoopCondition(fn: RsFunction, element: PsiElement): Boolean {
            val block = fn.block!!
            val statements = block.stmtList

            val hasStatements = when (block.expr) {
                null -> statements.size > 1 ||
                    statements.size == 1 && statements[0].descendantsOfType<RsRetExpr>().isEmpty()
                else -> statements.size > 0
            }

            return hasStatements && element.ancestorOrSelf<RsWhileExpr>() != null
        }
    }

    private fun inlineWithLetBindingsAdded(ref: RsReference, function: RsFunction) {
        val functionDup = function.copy() as RsFunction
        val body = functionDup.block ?: throw IllegalArgumentException("Cannot inline an empty function")
        replaceLastExprToStatement(body)
        val enclosingStatement = ref.element.ancestorOrSelf<RsStmt>()
            ?: ref.element.ancestorOrSelf<RsExpr>() ?: throw IllegalArgumentException("Cannot inline reference without expression parent.")
        val blockParent = enclosingStatement.ancestorOrSelf<RsBlock>()
        val childContainingEnclosingStatement = if (blockParent?.children?.isNotEmpty() == true) {
            blockParent.children.first { it.isAncestorOf(enclosingStatement) }
        } else {
            null
        }
        if (!enclosingStatement.isWritable) {
            throw IllegalArgumentException("Cannot inline into a readonly statement")
        }

        val caller = ref.element.ancestors.filter { it is RsCallExpr || it is RsDotExpr }.firstOrNull()
            ?: throw IllegalArgumentException("Cannot inline function without caller")

        checkIfFuncArgumentsMatchCallArguments(functionDup, caller)

        val funcScope = LocalSearchScope(body)

        val selfParam = functionDup.selfParameter
        replaceSelfParamWithExpr(selfParam, caller, funcScope)

        val retExprInside = body.descendantsOfType<RsRetExpr>().firstOrNull()?.expr
        if (retExprInside != null && body.stmtList.size == 1) {
            caller.replace(retExprInside)
            return
        }
        replaceCallerWithRetExpr(body, caller)

        val leftSibling = enclosingStatement.leftSiblings.firstOrNull { it is RsStmt }
        val rightSibling = enclosingStatement.rightSiblings.firstOrNull { it is RsStmt }
        if (blockParent != null) {
            if (enclosingStatement.isPhysical) {
                body.children.filter { it.text != ";" }.forEach {
                    enclosingStatement.addLeftSibling(it)
                }
            } else {
                addFunctionBodyToCallerWithCorrectSpacing(leftSibling, rightSibling, blockParent, body, childContainingEnclosingStatement)
            }

            blockParent.children.filter { it.text == ";" }.forEach { it.delete() }
        }
        enclosingStatement.addLeftSibling(factory.createNewline())

        enclosingStatement.ancestorOrSelf<RsBlock>()?.let {
            it.replace(CodeStyleManager.getInstance(project).reformat(it))
        }

        if (enclosingStatement.descendantsOfType<RsExpr>().isEmpty() || enclosingStatement.text == ";") {
            enclosingStatement.delete()
        }
    }

    private fun addFunctionBodyToCallerWithCorrectSpacing(leftSibling: PsiElement?, rightSibling: PsiElement?, blockParent: RsBlock, body: RsBlock, childContainingEnclosingStatement: PsiElement?) {
        if (leftSibling != null) {
            val isThereSpaceBeforeFunctionCall = leftSibling.nextSibling.text.startsWith("\n\n")
            val isThereSpaceAfterFunctionCall = rightSibling?.prevSibling?.text?.startsWith("\n\n") == true
            blockParent.addRangeAfter(body.children.first(), body.children.last(), leftSibling)
            if (isThereSpaceBeforeFunctionCall || isThereSpaceAfterFunctionCall) {
                blockParent.addAfter(factory.createWhitespace("\n\n    "), leftSibling)
            }
        } else if (rightSibling != null) {
            val isThereSpaceAfterFunctionCall = rightSibling.prevSibling.text.startsWith("\n\n")
            blockParent.addRangeBefore(body.children.first(), body.children.last(), rightSibling)
            if (isThereSpaceAfterFunctionCall) {
                blockParent.addBefore(factory.createWhitespace("\n\n    "), rightSibling)
            }
        } else if (childContainingEnclosingStatement?.isPhysical == true) {
            blockParent.addBefore(factory.createWhitespace("\n    "), childContainingEnclosingStatement)
            blockParent.addRangeBefore(body.children.first(), body.children.last(), childContainingEnclosingStatement)
            blockParent.addBefore(factory.createNewline(), childContainingEnclosingStatement)
        } else {
            blockParent.addStatements(body.children)
        }
    }

    private fun replaceCallerWithRetExpr(body: RsBlock, caller: PsiElement) {
        val retExpr = body.descendantsOfType<RsRetExpr>().firstOrNull()
        if (retExpr != null) {
            val retExprInside = retExpr.expr
            if (retExprInside != null) {


                // Covering a case in which ; isn't included in the expression, and parent is too wide.
                // e.g. if RsExpr is surrounded by RsBlock going to the RsBlock won't help.
                val actualRetExpr: PsiElement = if (retExpr.parent.text == retExpr.text + ";") {
                    retExpr.parent
                } else {
                    retExpr
                }
                if (caller.parent !is RsBlock && caller.parent.text != caller.text + ";") {
                    caller.replace(retExprInside)
                    retExpr.delete()
                } else {
                    actualRetExpr.replace(retExprInside)
                    if (caller.parent.text == caller.text + ";") {
                        caller.parent.delete()
                    } else {
                        caller.delete()
                    }
                }
            }
        } else {
            caller.delete()
        }
    }

    private fun replaceSelfParamWithExpr(selfParam: RsSelfParameter?, caller: PsiElement, funcScope: LocalSearchScope) {
        if (selfParam != null) {
            val selfExprText = buildString {
                if (selfParam.mut != null) append("mut")
                append(" ")
                append((caller as RsDotExpr).expr.text)
            }

            val selfExpr = factory.tryCreateExpression(selfExprText)!!
            ReferencesSearch.search(selfParam.navigationElement, funcScope).findAll().forEach {
                it.element.ancestorOrSelf<RsExpr>()!!.replace(selfExpr)
            }
        }
    }

    private fun checkIfFuncArgumentsMatchCallArguments(functionDup: RsFunction, caller: PsiElement) {
        val funcArguments = functionDup.valueParameters
        val callArguments = when (caller) {
            is RsCallExpr -> {
                caller.valueArgumentList.exprList
            }
            is RsDotExpr -> {
                val methodCall = caller.methodCall
                    ?: throw IllegalArgumentException("Cannot inline field lookup")
                methodCall.valueArgumentList.exprList
            }
            else -> throw IllegalArgumentException("Unknown caller expression type")
        }

        if (funcArguments.size != callArguments.size) {
            throw IllegalArgumentException(
                "Cannot inline function to references with mismatching arguments")
        }
    }

    private fun replaceLastExprToStatement(body: RsBlock) {
        val expr = body.expr ?: return
        val text = buildString {
            if (expr !is RsRetExpr) append("return ")
            append(expr.text)
            append(";")
        }

        val stmt = factory.createStatement(text)
        expr.replace(stmt)
    }

    private fun PsiElement.addLeftSibling(element: PsiElement) {
        this.parent.addBefore(element, this)
    }
}
