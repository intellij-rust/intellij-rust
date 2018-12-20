/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inline

import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.annotator.isMut
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.RsPathReference
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.factory
import org.rust.lang.core.types.type

class RsInlineMethodProcessor(val factory: RsPsiFactory)  {
    companion object {
        fun checkMultipleReturns(fn: RsFunction): Boolean {
            var returnsCount = fn.descendantsOfType<RsRetExpr>()
                .filter { it.ancestorStrict<RsFunction>() == fn }
                .size

            fn.block!!.expr?.let {
                if (it !is RsRetExpr) {
                    ++returnsCount
                }
            }
            return returnsCount > 1
        }

        fun checkRecursiveCall(fn: RsFunction) : Boolean {
            return fn.descendantsOfType<RsPath>()
                .map { it.reference.resolve() }
                .any { it == fn}
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

    fun inlineWithLetBindingsAdded(ref: RsReference, function: RsFunction) {
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

        if (funcArguments.size != callArguments.size) {
            return // TODO: throw
        }

        // TODO: deal with self parameter
        if (body.stmtList.isEmpty() && body.expr != null) {
            caller.replace(body.expr!!)
            return
        }

        val letBindings = mutableListOf<RsLetDecl>()
        val funcScope = LocalSearchScope(body)
        callArguments.zip(funcArguments).forEach zip@{

            if (it.first is RsLitExpr) {
                val binding = it.second.descendantOfTypeStrict<RsPatBinding>()!!
                ReferencesSearch.search(binding, funcScope).findAll().forEach {
                    ref -> ref.element.ancestorOrSelf<RsExpr>()!!.replace(it.first)
                }
                return@zip
            }

            val name = it.second.patText
            val expr = it.first
            val mutable = it.second.typeReference?.isMut ?: false
            val ty = factory.tryCreateType(expr.type.toString())

            val letBinding = factory.createLetDeclaration(name!!, expr, mutable, ty)
            letBindings.add(letBinding)
        }

        letBindings.forEach {
            body.addAfter(factory.createNewline(), body.lbrace)
            body.addAfter(it, body.lbrace)
        }


        val retExpr = body.descendantsOfType<RsRetExpr>().firstOrNull()
        if (retExpr != null && retExpr.expr != null) {
            val statements = body.stmtList
            if (statements.size == 1) {
                caller.replace(retExpr.expr!!)
                return
            }

            val retName = "ret"
            val retMutable = false
            val retType = function.retType?.typeReference

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
