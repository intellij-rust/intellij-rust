/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantsOfType

class RsInlineMethodProcessor {
    companion object {
        fun checkMultipleReturns(fn: RsFunction): Boolean {
            var returnsCount = fn.descendantsOfType<RsRetExpr>()
                .filter { it.ancestorStrict<RsFunction>() == fn }
                .size

            fn.block?.rbrace?.prevSibling?.let {
                if (it is RsExpr && it !is RsRetExpr) {
                    ++returnsCount
                }
            }

            return returnsCount > 1
        }

        fun checkRecursiveCall(fn: RsFunction) : Boolean {
            fn.descendantsOfType<RsMethodCall>().forEach {
                if (getFunction(it) == fn) {
                    return true
                }
            }

            fn.descendantsOfType<RsCallExpr>().forEach {
                if (getFunction(it) == fn) {
                    return true
                }
            }

            return false
        }

        fun getFunction(element: PsiElement) : RsFunction? {
            return when(element) {
                is RsFunction -> element
                is RsCallExpr -> (element.expr as? RsPathExpr)?.path
                is RsMethodCall -> element.reference.resolve()
                else -> null
            }?.let { it.reference?.resolve() as? RsFunction }
        }
    }


    private fun collectElements(start: PsiElement, stop: PsiElement?, pred: (PsiElement) -> Boolean): Array<out PsiElement> {
        check(stop == null || start.parent == stop.parent)

        val psiSeq = generateSequence(start) {
            if (it.nextSibling == stop)
                null
            else
                it.nextSibling
        }

        return PsiUtilCore.toPsiElementArray(psiSeq.filter(pred).toList())
    }
}
