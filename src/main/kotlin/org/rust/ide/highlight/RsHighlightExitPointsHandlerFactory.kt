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
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.parentOfType

class RsHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is RsFile) {
            return null
        }
        val elementType = target.elementType
        if (elementType == RETURN || elementType == TRY_EXPR || elementType == Q) {
            return RsHighlightExitPointsHandler(editor, file, target)
        }
        if (elementType == IDENTIFIER) {
            val parent = target.parent
            if (parent is RsMacroCall && (parent.referenceName == "panic" || parent.referenceName == "try")) {
                return RsHighlightExitPointsHandler(editor, file, target)
            }
        }
        if (testForExprBeforeBlock(target)) {
            return RsHighlightExitPointsHandler(editor, file, target)
        }
        return null
    }
     private fun testForExprBeforeBlock(target: PsiElement) : Boolean {
         var context = target
         while (context != null) {
             val parent = context.parent
             if (context is RsExpr && parent is RsBlock) {
                 return true
             }
             context = context.parent
         }
         return false
     }

    private class RsHighlightExitPointsHandler(editor: Editor, file: PsiFile, var target: PsiElement) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
        override fun getTargets() = listOf(target)

        override fun selectTargets(targets: MutableList<PsiElement>?, selectionConsumer: Consumer<MutableList<PsiElement>>?) {
            selectionConsumer?.consume(targets)
        }

        override fun computeUsages(targets: MutableList<PsiElement>?) {
            val function = target.parentOfType<RsFunction>() ?: return
            function.accept(object : RsVisitor() {
                override fun visitElement(element: PsiElement?) {
                    element?.acceptChildren(this)
                }

                override fun visitRetExpr(o: RsRetExpr) {
                    addOccurrence(o)
                }

                override fun visitTryExpr(o: RsTryExpr) {
                    addOccurrence(o)
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
                    if (o.parent is RsBlock) {
                        addOccurrence(o)
                    } else {
                        super.visitExpr(o)
                    }
                }
            })
        }

    }

}
