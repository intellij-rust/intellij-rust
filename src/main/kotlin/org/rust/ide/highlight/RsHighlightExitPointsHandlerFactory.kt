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
import org.rust.lang.core.ExitPoint
import org.rust.lang.core.psi.RsElementTypes.Q
import org.rust.lang.core.psi.RsElementTypes.RETURN
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType

class RsHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is RsFile) return null

        val elementType = target.elementType
        if (elementType == RETURN || elementType == Q) {
            return RsHighlightExitPointsHandler(editor, file, target)
        }
        return null
    }
}

private class RsHighlightExitPointsHandler(editor: Editor, file: PsiFile, var target: PsiElement) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
    override fun getTargets() = listOf(target)

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<List<PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: MutableList<PsiElement>?) {
        val sink: (ExitPoint) -> Unit = { exitPoint ->
            when (exitPoint) {
                is ExitPoint.Return -> addOccurrence(exitPoint.e)
                is ExitPoint.TryExpr -> if (exitPoint.e is RsTryExpr) addOccurrence(exitPoint.e.q) else addOccurrence(exitPoint.e)
                is ExitPoint.DivergingExpr -> addOccurrence(exitPoint.e)
                is ExitPoint.TailExpr -> addOccurrence(exitPoint.e)
                is ExitPoint.TailStatement -> Unit
            }
        }

        val fnOrLambda = target.ancestors.firstOrNull { it is RsFunction || it is RsLambdaExpr }
        when (fnOrLambda) {
            is RsFunction -> ExitPoint.process(fnOrLambda, sink)
            is RsLambdaExpr -> ExitPoint.process(fnOrLambda, sink)
        }
    }
}

