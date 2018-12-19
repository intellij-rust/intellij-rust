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
import org.rust.lang.core.stubs.factory
import org.rust.lang.core.types.type

class RsInlineMethodProcessor(val factory: RsPsiFactory)  {
    companion object {
        fun replaceLastExprToStatement(fn: RsFunction, factory: RsPsiFactory) {
            val expr = fn.block!!.expr ?: return
            var text = ""
            if (expr !is RsRetExpr)
                text += "return "
            text += expr.text
            text += ";"

//            val text = buildString {
//                if (expr !is RetExpt) append "return "
//                append expr.text
//            }

            val stmt = factory.createStatement(text)
            expr.replace(stmt)
        }

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

    fun doSillyInline(ref: RsPathReference, body: RsBlock, factory: RsPsiFactory) {
        body.lbrace.delete()
        body.rbrace?.delete()
        val enclosingStatement = ref.element.ancestorOrSelf<RsStmt>() ?: return
        enclosingStatement.parent.addBefore(body, enclosingStatement)
        enclosingStatement.parent.addBefore(factory.createNewline(), enclosingStatement)
    }

    fun inlineWithLetBindingsAdded(ref: RsPathReference, function: RsFunction, factory: RsPsiFactory) {
        val body = function.block!!.copy() as RsBlock
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
//            val retAssignment = factory.createStatement("$retName = $retExpr")
            val retVar = factory.createExpression(retName)
//            retExpr.replace(retAssignment)
            caller.replace(retVar)
        }

        letBindings.forEach {
            body.addAfter(factory.createNewline(), body.lbrace)
            body.addAfter(it, body.lbrace)

        }


        enclosingStatement.parent.addBefore(body, enclosingStatement)
        enclosingStatement.parent.addBefore(factory.createNewline(), enclosingStatement)
    }




//    private fun collectElements(start: PsiElement, stop: PsiElement?, pred: (PsiElement) -> Boolean): Array<out PsiElement> {
//        check(stop == null || start.parent == stop.parent)
//
//        val psiSeq = generateSequence(start) {
//            if (it.nextSibling == stop)
//                   null
//            else
//                it.nextSibling
//        }
//
//        return PsiUtilCore.toPsiElementArray(psiSeq.filter(pred).toList())
//    }
}
