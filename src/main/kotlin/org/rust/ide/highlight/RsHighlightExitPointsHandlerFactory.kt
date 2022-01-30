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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Consumer
import org.rust.lang.core.dfa.ExitPoint
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*

class RsHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is RsFile) return null

        val createHandler: (PsiElement) -> RsHighlightExitPointsHandler? = { element ->
            val elementType = element.elementType
            val shouldHighlightExitPoints = elementType == RETURN
                || elementType == Q && element.parent is RsTryExpr
                || elementType == BREAK
                || elementType == FN && element.parent is RsFunction
                || elementType == ARROW && element.parent.let { it is RsRetType && it.parent is RsFunctionOrLambda }
            if (shouldHighlightExitPoints) {
                RsHighlightExitPointsHandler(editor, file, element)
            } else {
                null
            }
        }
        val prevToken = PsiTreeUtil.prevLeaf(target) ?: return null
        return createHandler(target) ?: createHandler(prevToken)
    }

}

private class RsHighlightExitPointsHandler(
    editor: Editor,
    file: PsiFile,
    val target: PsiElement
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
    override fun getTargets() = listOf(target)

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        val usages = mutableListOf<PsiElement>()
        val sink: (ExitPoint) -> Unit = { exitPoint ->
            val element = when (exitPoint) {
                is ExitPoint.Return -> exitPoint.e
                is ExitPoint.TryExpr -> if (exitPoint.e is RsTryExpr) exitPoint.e.q else exitPoint.e
                is ExitPoint.DivergingExpr -> exitPoint.e
                is ExitPoint.TailExpr -> exitPoint.e
                is ExitPoint.InvalidTailStatement -> null
            }
            if (element != null && !element.isExpandedFromMacro) {
                usages += element
            }
        }

        for (ancestor in target.ancestors) {
            if (ancestor is RsBlockExpr && ancestor.isTry && target.elementType == Q) {
                break
            } else if (ancestor is RsBlockExpr && ancestor.isAsync) {
                ExitPoint.process(ancestor.block, sink)
                break
            } else if (ancestor is RsFunction) {
                ExitPoint.process(ancestor, sink)
                break
            } else if (ancestor is RsLambdaExpr) {
                ExitPoint.process(ancestor, sink)
                break
            }
        }

        // highlight only if target inside exit point
        val targetAncestors = target.ancestors.toSet()
        if (usages.any { it in targetAncestors } || target.elementType == FN || target.elementType == ARROW) {
            usages.forEach(this::addOccurrence)
        }
    }
}
