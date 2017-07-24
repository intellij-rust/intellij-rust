/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType

class RsHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is RsFile) {
            return null
        }
        val elementType = target.elementType
        if (elementType == RETURN || elementType == Q) {
            return RsHighlightExitPointsHandler(editor, file, target)
        }
        return null
    }

    private class RsHighlightExitPointsHandler(editor: Editor, file: PsiFile, var target: PsiElement) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
        override fun getTargets() = listOf(target)

        override fun selectTargets(targets: MutableList<PsiElement>?, selectionConsumer: Consumer<MutableList<PsiElement>>?) {
            selectionConsumer?.consume(targets)
        }

        private fun getFunctionOrLambda(target: PsiElement): PsiElement? =
            target.ancestors.first { it is RsFunction || it is RsLambdaExpr }

        private fun onlyExpressionAboveUntilFn(expr: RsExpr): Boolean {
            expr.ancestors.forEach {
                val parent = it.parent
                when (it) {
                    is RsExpr -> when (parent) {
                        is RsMatchExpr,
                        is RsPatRange,
                        is RsMatchArmGuard -> return false
                    }
                    is RsFunction,
                    is RsLambdaExpr -> return true
                    is RsCondition,
                    is RsStmt -> return false
                }
            }
            return false
        }

        override fun computeUsages(targets: MutableList<PsiElement>?) {
            val function: PsiElement? = getFunctionOrLambda(target)
            function?.acceptChildren(object : RsVisitor() {
                override fun visitElement(element: PsiElement?) {
                    element?.acceptChildren(this)
                }

                override fun visitFunction(o: RsFunction) {}
                override fun visitLambdaExpr(o: RsLambdaExpr) {}
                override fun visitRetExpr(o: RsRetExpr) = addOccurrence(o)

                override fun visitTryExpr(o: RsTryExpr) {
                    (o.expr as? RsMethodCallExpr)?.acceptChildren(this)
                    addOccurrence(o.q)
                }

                override fun visitTryMacroArgument(o: RsTryMacroArgument) {
                    val macroCall = o.parent as? RsMacroCall ?: return
                    addOccurrence(macroCall)
                }

                override fun visitFormatMacroArgument(o: RsFormatMacroArgument) {
                    val macroCall = o.parent as? RsMacroCall ?: return
                    if (macroCall.referenceName == "panic") {
                        addOccurrence(macroCall)
                    }
                }

                override fun visitExpr(o: RsExpr) {
                    when (o) {
                        is RsIfExpr,
                        is RsBlockExpr,
                        is RsMatchExpr -> o.acceptChildren(this)
                        else -> if (onlyExpressionAboveUntilFn(o)) {
                            addOccurrence(o)
                        } else {
                            super.visitExpr(o)
                        }
                    }
                }
            })
        }

    }

}
