/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling

class RsMethodLineSeparatorProvider(
    private val daemonSettings: DaemonCodeAnalyzerSettings,
    private val colorsManager: EditorColorsManager
) : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        if (daemonSettings.SHOW_METHOD_SEPARATORS) {
            if (element.canHaveSeparator()) {
                val prevSibling = element.getPrevNonCommentSibling()
                if (prevSibling.canHaveSeparator() && (element.wantsSeparator() || prevSibling.wantsSeparator())) {
                    return createLineSeparatorByElement(element)
                }
            }
        }
        return null
    }

    private fun PsiElement?.canHaveSeparator() = this is RsFunction

    private fun PsiElement?.wantsSeparator() = if (this == null) false else StringUtil.getLineBreakCount(text) > 0

    private fun createLineSeparatorByElement(element: PsiElement): LineMarkerInfo<PsiElement> {
        val anchor = PsiTreeUtil.getDeepestFirst(element)
        return LineMarkerInfo(anchor, anchor.textRange, null, Pass.LINE_MARKERS, null, null, GutterIconRenderer.Alignment.RIGHT).apply {
            separatorColor = colorsManager.globalScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
            separatorPlacement = SeparatorPlacement.TOP
        }
    }

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<LineMarkerInfo<PsiElement>>
    ) {
    }

}
