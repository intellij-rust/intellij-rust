package org.rust.ide.annotator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.FunctionUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustCallExprElement
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.psi.RustPathExprElement
import org.rust.lang.core.psi.util.parentOfType
import java.util.*

/**
 * Line marker provider that annotates recursive funciton and method calls with
 * an icon on the gutter.
 */
class RustRecursiveCallLineMarkerProvider : LineMarkerProvider {

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
                    RustIcons.RECURSIVE_CALL,
                    // BACKCOMPAT: 2016.2
                    Pass.UPDATE_OVERRIDDEN_MARKERS,
                    FunctionUtil.constant("Recursive call"),
                    null,
                    GutterIconRenderer.Alignment.RIGHT))
            }
        }
    }

    private val RustCallExprElement.pathExpr: RustPathExprElement?
        get() = expr as? RustPathExprElement

    private val PsiElement.isRecursive: Boolean get() {
        val def = when (this) {
            is RustCallExprElement -> pathExpr?.path?.reference?.resolve()
            is RustMethodCallExprElement -> reference.resolve()
            else -> null
        } ?: return false

        return parentOfType<RustFunctionElement>() == def
    }
}
