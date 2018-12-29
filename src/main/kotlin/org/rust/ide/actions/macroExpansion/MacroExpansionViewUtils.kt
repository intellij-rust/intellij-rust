/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.macroExpansion

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.popup.PopupPositionManager
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.getExpandedElementsFromMacroExpansion
import org.rust.lang.core.macros.parseExpandedTextWithContext
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*
import java.awt.BorderLayout
import javax.swing.JPanel

/** Data class to group title and expansions of macro to show them in the view. */
data class MacroExpansionViewDetails(
    val macroToExpand: RsMacroCall,
    val title: String,
    val expansions: List<RsExpandedElement>
)

/**
 * This method expands macro in background thread with progress bar showing on, allowing user to close it if expansion
 * takes too long.
 */
fun expandMacroForViewWithProgress(
    project: Project,
    ctx: RsMacroCall,
    expandRecursively: Boolean
): MacroExpansionViewDetails {
    val progressTitle = "${if (expandRecursively) "Recursive" else "Single step"} expansion progress..."

    return project.computeWithCancelableProgress(progressTitle) {
        runReadAction { expandMacroForView(ctx, expandRecursively) }
    }
}


/** This function shows macro expansion in floating popup. */
fun showMacroExpansionPopup(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
    if (expansionDetails.expansions.isEmpty()) return

    val formattedExpansion = reformatMacroExpansion(expansionDetails.macroToExpand, expansionDetails.expansions)

    val component = MacroExpansionViewComponent(formattedExpansion)

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
        .setProject(project)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .setTitle(expansionDetails.title)
        .createPopup()

    PopupPositionManager.positionPopupInBestPosition(popup, editor, null)
}

private fun expandMacroForView(macroToExpand: RsMacroCall, expandRecursively: Boolean): MacroExpansionViewDetails =
    MacroExpansionViewDetails(
        macroToExpand,
        getMacroExpansionViewTitle(macroToExpand, expandRecursively),
        getMacroExpansions(macroToExpand, expandRecursively)
    )

private fun<T> Project.computeWithCancelableProgress(title: String, supplier: () -> T): T {
    val manager = ProgressManager.getInstance()

    return manager.runProcessWithProgressSynchronously<T, Exception>(supplier, title, true, this)
}

private fun getMacroExpansionViewTitle(macroToExpand: RsMacroCall, expandRecursively: Boolean): String =
    if (expandRecursively) {
        "Recursive expansion of ${macroToExpand.macroName}! macro"
    } else {
        "First level expansion of ${macroToExpand.macroName}! macro"
    }

private fun getMacroExpansions(macroToExpand: RsMacroCall, expandRecursively: Boolean): List<RsExpandedElement> {
    if (!expandRecursively) {
        return macroToExpand.expansion ?: listOf(macroToExpand)
    }

    val expansionText = macroToExpand.expandAllMacrosRecursively()

    return RsPsiFactory(macroToExpand.project)
        .parseExpandedTextWithContext(macroToExpand, expansionText)
}

private fun reformatMacroExpansion(
    macroToExpand: RsMacroCall,
    expansions: List<RsExpandedElement>
): List<RsExpandedElement> {
    val file = expansions.first().containingFile as RsFile
    runWriteAction { formatPsiFile(file) }

    return getExpandedElementsFromMacroExpansion(macroToExpand, file)
}

/** Simple view to show some code. Inspired by [com.intellij.codeInsight.hint.ImplementationViewComponent] */
private class MacroExpansionViewComponent(expansions: List<RsExpandedElement>) : JPanel(BorderLayout()) {

    private val editor: EditorEx

    init {
        require(expansions.isNotEmpty()) { "Must be at least one expansion!" }

        val project = expansions.first().project

        editor = project.createReadOnlyEditorWithElements(expansions)
        setupSimpleEditorLook(editor)
        editor.highlighter = project.createRustHighlighter()

        add(editor.component)
    }

    private fun setupSimpleEditorLook(editor: EditorEx) {
        with(editor.settings) {
            additionalLinesCount = 1
            additionalColumnsCount = 1
            isLineMarkerAreaShown = false
            isIndentGuidesShown = false
            isLineNumbersShown = false
            isFoldingOutlineShown = false
        }
    }

    /**
     * Every editor, created by [EditorFactory], should be released
     * (see docs for [EditorFactory.createEditor] method).
     */
    override fun removeNotify() {
        super.removeNotify()

        EditorFactory.getInstance().releaseEditor(editor)
    }
}

private fun formatPsiFile(element: PsiFile) {
    CodeStyleManager.getInstance(element.project).reformatText(element, element.startOffset, element.endOffset)
}

private fun Project.createReadOnlyEditorWithElements(expansions: Collection<PsiElement>): EditorEx {
    val factory = EditorFactory.getInstance()

    val text = expansions.joinToString("\n") { it.text }
    val doc = factory.createDocument(text)
    doc.setReadOnly(true)

    return factory.createEditor(doc, this) as EditorEx
}

fun Project.createRustHighlighter(): EditorHighlighter =
    HighlighterFactory.createHighlighter(this, RsFileType)
