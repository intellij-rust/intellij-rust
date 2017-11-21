/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.FunctionUtil
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.resolve.ref.RsReference
import java.util.*

/**
 * Line marker provider that annotates recursive function and method calls with
 * an icon on the gutter.
 */
class RsRecursiveCallLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>,
                                        result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        val lines = HashSet<Int>()  // To prevent several markers on one line

        for (el in elements) {
            val parent = el.parent
            val isRecursive = when {
                parent is RsMethodCall && el == parent.identifier && parent.reference.isRecursive -> true
                parent is RsPath && el == parent.identifier -> {
                    val expr = parent.parent as? RsPathExpr
                    val call = expr?.parent as? RsCallExpr
                    expr != null && call != null && call.expr == expr && parent.reference.isRecursive
                }
                else -> false
            }
            if (!isRecursive) continue
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

    private val RsReference.isRecursive: Boolean get() {
        val def = resolve()
        return def != null && element.ancestorStrict<RsFunction>() == def
    }
}
