/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inline

import com.intellij.psi.PsiElement
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
                null -> statements.size > 1 || statements.size == 1 && statements[0].descendantsOfType<RsRetExpr>().isEmpty()
                else -> statements.size > 0
            }

            return hasStatements && element.ancestorOrSelf<RsWhileExpr>() != null
        }
    }

    fun inlineWithLetBindingsAdded(ref: RsReference, function: RsFunction) {
        val body = function.block!!.copy() as RsBlock
        replaceLastExprToStatement(body)
        val enclosingStatement = ref.element.ancestorOrSelf<RsStmt>() ?: return
        val caller = ref.element.ancestors
            .filter{it is RsCallExpr || it is RsDotExpr }.firstOrNull() ?: return

        val funcArguments = function.valueParameters
        val callArguments = when (caller) {
            is RsCallExpr -> {caller.valueArgumentList.exprList}
            is RsDotExpr -> {caller.methodCall?.valueArgumentList?.exprList}
            else -> null
        } ?: return

        if (funcArguments.size != callArguments.size) {
            return
        }

        val letBindings = mutableListOf<RsLetDecl>()
        callArguments.zip(funcArguments).forEach {
            val name = it.second.patText
            val expr = it.first
            val mutable = it.second.typeReference?.isMut ?: false
            val ty = factory.tryCreateType(expr.type.toString())

            val letBinding = factory.createLetDeclaration(name!!, expr, mutable, ty)
            letBindings.add(letBinding)
        }

        val retExpr = body.descendantsOfType<RsRetExpr>().firstOrNull()
        if (retExpr != null && retExpr.expr != null) {
            val retName = "ret"
            val retMutable = false
            val retType = function.retType?.typeReference

            val retLetBinding = factory.createLetDeclaration(retName, null, retMutable, retType)

            enclosingStatement.parent.addBefore(factory.createNewline(), enclosingStatement)
            enclosingStatement.parent.addBefore(retLetBinding, enclosingStatement)
            enclosingStatement.parent.addBefore(factory.createNewline(), enclosingStatement)
            retExpr.replace(factory.createExpression("$retName = ${retExpr.expr!!.text}"))
            val retVar = factory.createExpression(retName)
            caller.replace(retVar)
        }

        letBindings.forEach {
            body.addAfter(factory.createNewline(), body.lbrace)
            body.addAfter(it, body.lbrace)

        }

        enclosingStatement.parent.addBefore(body, enclosingStatement)
        enclosingStatement.parent.addBefore(factory.createNewline(), enclosingStatement)
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
}
