/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.buildList

class RsInlineMethodProcessor {
    companion object {
        fun checkMultipleReturns(fn: RsFunction): Boolean {
            var returnsCount = fn.descendantsOfType<RsRetExpr>()
                .filter { it.ancestorStrict<RsFunction>() == fn }
                .size

//            fn.block?.rbrace?.prevSibling?.let {
//                if (it is RsExpr && it !is RsRetExpr) {
//                    ++returnsCount
//                }
//            }

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
