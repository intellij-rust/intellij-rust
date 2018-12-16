/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inline

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsInlineMethodProcessor {
    companion object {
        fun replaceLastExprToStatement(fn: RsFunction, factory: RsPsiFactory) {
            val expr = fn.block!!.expr ?: return
            var text = ""
            if (expr !is RsRetExpr)
                text += "return "
            text += expr.text
            text += ";"

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
