/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.FunctionUtil
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.parentOfType
import java.util.*

/**
 * Line marker provider that annotates recursive funciton and method calls with
 * an icon on the gutter.
 */
class RsRecursiveCallLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>,
                                        result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        val lines = HashSet<Int>()  // To prevent several markers on one line

        for (el in elements.filter { it.isRecursive }) {
            val doc = PsiDocumentManager.getInstance(el.project).getDocument(el.containingFile) ?: continue
            val lineNumber = doc.getLineNumber(el.textOffset)
            if (lineNumber !in lines) {
                lines.add(lineNumber)
                result.add(LineMarkerInfo(
                    el,
                    el.textRange,
                    RsIcons.RECURSIVE_CALL,
                    Pass.LINE_MARKERS,
                    FunctionUtil.constant("Recursive call"),
                    null,
                    GutterIconRenderer.Alignment.RIGHT))
            }
        }
    }

    private val RsCallExpr.pathExpr: RsPathExpr?
        get() = expr as? RsPathExpr

    private val PsiElement.isRecursive: Boolean get() {
        val def = when (this) {
            is RsCallExpr -> pathExpr?.path?.reference?.resolve()
            is RsMethodCallExpr -> reference.resolve()
            else -> null
        } ?: return false

        return parentOfType<RsFunction>() == def
    }
}
