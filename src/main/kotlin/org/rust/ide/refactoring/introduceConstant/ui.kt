/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.introduceConstant

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.block
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class Highlighter(private val editor: Editor) : JBPopupListener {
    private var highlighter: RangeHighlighter? = null
    private val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)

    fun onSelect(candidate: InsertionCandidate) {
        dropHighlighter()
        val markupModel: MarkupModel = editor.markupModel

        val textRange = candidate.parent.textRange
        highlighter = markupModel.addRangeHighlighter(
            textRange.startOffset, textRange.endOffset, HighlighterLayer.SELECTION - 1, attributes,
            HighlighterTargetArea.EXACT_RANGE)
    }

    override fun onClosed(event: LightweightWindowEvent) {
        dropHighlighter()
    }

    private fun dropHighlighter() {
        highlighter?.dispose()
    }
}

fun showInsertionChooser(
    editor: Editor,
    expr: RsExpr,
    callback: (InsertionCandidate) -> Unit
) {
    val candidates = findInsertionCandidates(expr)
    if (isUnitTestMode) {
        callback(MOCK!!.chooseInsertionPoint(expr, candidates))
    } else {
        val highlighter = Highlighter(editor)
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(candidates)
            .setRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?,
                                                          value: Any,
                                                          index: Int,
                                                          isSelected: Boolean,
                                                          cellHasFocus: Boolean): Component {
                    val candidate = value as InsertionCandidate
                    val text = candidate.description()
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            })
            .setItemSelectedCallback { value: InsertionCandidate? ->
                if (value == null) return@setItemSelectedCallback
                highlighter.onSelect(value)
            }
            .setTitle("Choose scope to introduce constant ${expr.text}")
            .setMovable(true)
            .setResizable(false)
            .setRequestFocus(true)
            .setItemChosenCallback { it?.let { callback(it) } }
            .addListener(highlighter)
            .createPopup()
            .showInBestPositionFor(editor)
    }
}

interface ExtractConstantUi {
    fun chooseInsertionPoint(expr: RsExpr, candidates: List<InsertionCandidate>): InsertionCandidate
}

data class InsertionCandidate(val context: PsiElement, val parent: PsiElement, val anchor: PsiElement) {
    fun description(): String = when (val element = this.context) {
        is RsFunction -> "fn ${element.name}"
        is RsModItem -> "mod ${element.name}"
        is RsFile -> "file"
        else -> error("unreachable")
    }
}

private fun findInsertionCandidates(expr: RsExpr): List<InsertionCandidate> {
    var parent: PsiElement = expr
    var anchor: PsiElement = expr
    val points = mutableListOf<InsertionCandidate>()

    fun getAnchor(parent: PsiElement, anchor: PsiElement): PsiElement {
        var found = anchor
        while (found.parent != parent) {
            found = found.parent
        }
        return found
    }

    var moduleVisited = false
    while (parent !is RsFile) {
        parent = parent.parent
        when (parent) {
            is RsFunction -> {
                if (!moduleVisited) {
                    parent.block?.let {
                        points.add(InsertionCandidate(parent, it, getAnchor(it, anchor)))
                        anchor = parent
                    }
                }
            }
            is RsModItem, is RsFile -> {
                points.add(InsertionCandidate(parent, parent, getAnchor(parent, anchor)))
                anchor = parent
                moduleVisited = true
            }
        }
    }
    return points
}

var MOCK: ExtractConstantUi? = null

@TestOnly
fun withMockExtractConstantChooser(mock: ExtractConstantUi, f: () -> Unit) {
    MOCK = mock
    try {
        f()
    } finally {
        MOCK = null
    }
}
