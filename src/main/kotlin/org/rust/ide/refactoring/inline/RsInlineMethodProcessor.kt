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
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.rust.ide.annotator.isMut
import org.rust.ide.refactoring.introduceVariable.suggestedNames
import org.rust.lang.core.cfg.ExitPoint
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsMethodCallReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.factory
import org.rust.lang.core.types.type

class RsInlineMethodProcessor(
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
        runReadAction {
            usages.addAll(ReferencesSearch.search(function, projectScope).findAll())
        }

        val usagesAsReference = usages.filter {
                it.element.ancestorOrSelf<RsCallExpr>() == null
                    && it.element.ancestorOrSelf<RsMethodCall>() == null
            }

        if (!usagesAsReference.isEmpty() && removeDefinition) {
            throw IllegalArgumentException(
                "Cannot remove function definition: function pointer inline is not supported")
        }

        usages.removeAll(usagesAsReference)
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        runWriteAction {
            usages.asIterable().forEach loop@{
                val reference = it.reference as? RsReference ?: return@loop
                inlineWithLetBindingsAdded(reference, function)
            }
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

            override fun getProcessedElementsHeader() = "Method to inline"
        }
    }

    companion object {
        fun checkMultipleReturns(fn: RsFunction): Boolean {
            var entryCount = 0
            val sink: (ExitPoint) -> Unit = {
                if (it !is ExitPoint.TryExpr) {
                    ++entryCount
                }
            }
            ExitPoint.process(fn, sink)
            return entryCount > 1
        }

        fun checkRecursiveCall(fn: RsFunction) : Boolean {
            return fn.descendantsOfType<RsPath>().any { it.reference.resolve() == fn}
        }

        fun checkIfLoopCondition(fn: RsFunction, element: PsiElement): Boolean {
            val block = fn.block!!
            val statements = block.stmtList

            val hasStatements = when(block.expr) {
                null -> statements.size > 1 ||
                        statements.size == 1 && statements[0].descendantsOfType<RsRetExpr>().isEmpty()
                else -> statements.size > 0
            }

            return hasStatements && element.ancestorOrSelf<RsWhileExpr>() != null
        }
    }

    private fun inlineWithLetBindingsAdded(ref: RsReference, function: RsFunction) {
        val functionDup = function.copy() as RsFunction
        val body = functionDup.block!!
        replaceLastExprToStatement(body)
        val enclosingStatement = ref.element.ancestorOrSelf<RsStmt>() ?: return
        val caller = ref.element.ancestors
            .filter{it is RsCallExpr || it is RsDotExpr }.firstOrNull() ?: return

        val funcArguments = functionDup.valueParameters
        val callArguments = when (caller) {
            is RsCallExpr -> {caller.valueArgumentList.exprList}
            is RsDotExpr -> {caller.methodCall?.valueArgumentList?.exprList}
            else -> null
        } ?: return

        if (funcArguments.size > callArguments.size) {
            return // TODO: throw
        }

        val letBindings = mutableListOf<RsLetDecl>()
        val funcScope = LocalSearchScope(body)
        callArguments.zip(funcArguments).forEach zip@{

            val binding = it.second.descendantOfTypeStrict<RsPatBinding>()!!
            if (!binding.isMut && it.first is RsLitExpr) {
                ReferencesSearch.search(binding, funcScope).findAll().forEach {
                    ref -> ref.element.ancestorOrSelf<RsExpr>()!!.replace(it.first)
                }
                return@zip
            }

            val expr = it.first
            val name = it.second.patText ?: expr.suggestedNames().default
            val mutable = binding.isMut
            val ty = factory.tryCreateType(expr.type.toString())

            val letBinding = factory.createLetDeclaration(name, expr, mutable, ty)
            letBindings.add(letBinding)
        }

        val selfParam = functionDup.selfParameter
        if (selfParam != null) {
            val selfExprText = buildString {
                if (selfParam.and != null) append("&")
                if (selfParam.mut != null) append("mut")
                append(" ")
                append((caller as RsDotExpr).expr.text)
            }

            val selfExpr = factory.tryCreateExpression(selfExprText)!!
            val selfName = selfExpr.suggestedNames().default
            val mutable = false
            val ty = factory.tryCreateType(selfExpr.type.toString())

            val letBinding = factory.createLetDeclaration(selfName, selfExpr, mutable, ty)
            letBindings.add(letBinding)

            val selfVariable = factory.tryCreateExpression(selfName)!!
            ReferencesSearch.search(selfParam.navigationElement, funcScope).findAll().forEach{
                it.element.ancestorOrSelf<RsExpr>()!!.replace(selfVariable)
            }
        }

        letBindings.forEach {
            body.addAfter(it, body.lbrace)
            body.addAfter(factory.createNewline(), body.lbrace)
        }

        val retExpr = body.descendantsOfType<RsRetExpr>().firstOrNull()
        if (retExpr != null && retExpr.expr != null) {
            val statements = body.stmtList
            val expr = retExpr.expr!!
            if (statements.size == 1) {
                caller.replace(expr)
                return
            }

            val retName = retExpr.suggestedNames().default
            val retMutable = false
            val retType = factory.tryCreateType(expr.type.toString())

            val retLetBinding = factory.createLetDeclaration(retName, null, retMutable, retType)

            enclosingStatement.addLeftSibling(factory.createNewline())
            enclosingStatement.addLeftSibling(retLetBinding)
            enclosingStatement.addLeftSibling(factory.createNewline())
            retExpr.replace(factory.createExpression("$retName = ${retExpr.expr!!.text}"))
            val retVar = factory.createExpression(retName)
            caller.replace(retVar)
        } else {
            caller.delete()
        }

        enclosingStatement.addLeftSibling(body)
        enclosingStatement.addLeftSibling(factory.createNewline())

        if (enclosingStatement.descendantsOfType<RsExpr>().isEmpty()) {
            enclosingStatement.delete()
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
