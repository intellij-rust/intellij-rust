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
import org.rust.lang.core.cfg.ExitPoint
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*

class RsHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is RsFile) return null

        val createHandler: (PsiElement) -> RsHighlightExitPointsHandler? = { element ->
            val elementType = element.elementType
            if (elementType == RETURN || (elementType == Q && element.parent is RsTryExpr) || elementType == BREAK) {
                RsHighlightExitPointsHandler(editor, file, element)
            } else null
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

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: ExitPointSelectionConsumer) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        val usages = mutableListOf<PsiElement>()
        val sink: (ExitPoint) -> Unit = { exitPoint ->
            when (exitPoint) {
                is ExitPoint.Return -> usages.add(exitPoint.e)
                is ExitPoint.TryExpr -> if (exitPoint.e is RsTryExpr) usages.add(exitPoint.e.q) else usages.add(exitPoint.e)
                is ExitPoint.DivergingExpr -> usages.add(exitPoint.e)
                is ExitPoint.TailExpr -> usages.add(exitPoint.e)
                is ExitPoint.TailStatement -> Unit
            }
        }

        for (ancestor in target.ancestors) {
            if (ancestor is RsBlockExpr && ancestor.isTry && target.elementType == Q) {
                break
            } else if (ancestor is RsBlockExpr && ancestor.isAsync) {
                ExitPoint.process(ancestor.block, sink)
                break
            } else if (ancestor is RsFunction) {
                ExitPoint.process(ancestor.block, sink)
                break
            } else if (ancestor is RsLambdaExpr) {
                ExitPoint.process(ancestor.expr, sink)
                break
            }
        }

        // highlight only if target inside exit point
        if (usages.any { target.ancestors.contains(it) }) {
            usages.forEach(this::addOccurrence)
        }
    }
}
