/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.macroExpansion

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.popup.PopupPositionManager
import com.intellij.util.DocumentUtil
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.expansionContext
import org.rust.lang.core.macros.getExpansionFromExpandedFile
import org.rust.lang.core.macros.parseExpandedTextWithContext
import org.rust.lang.core.psi.RsProcMacroPsiUtil
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsPsiManager
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.computeWithCancelableProgress
import java.awt.BorderLayout
import javax.swing.JPanel

const val FAILED_TO_EXPAND_MESSAGE = "Failed to expand the macro"

/** Data class to group title and expansions of macro to show them in the view. */
data class MacroExpansionViewDetails(
    val macroToExpand: RsPossibleMacroCall,
    val title: String,
    val expansion: MacroExpansion
)

/**
 * This method expands macro in background thread with progress bar showing on, allowing user to close it if expansion
 * takes too long.
 */
fun expandMacroForViewWithProgress(
    project: Project,
    ctx: RsPossibleMacroCall,
    expandRecursively: Boolean
): MacroExpansionViewDetails? {
    val progressTitle = "${if (expandRecursively) "Recursive" else "Single step"} expansion progress..."
    return project.computeWithCancelableProgress(progressTitle) {
        runReadAction { expandMacroForView(ctx, expandRecursively) }
    }
}


/** This function shows macro expansion in floating popup. */
fun showMacroExpansionPopup(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
    if (expansionDetails.expansion.elements.isEmpty()) return

    val formattedExpansion = reformatMacroExpansion(expansionDetails.macroToExpand, expansionDetails.expansion)

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

private fun expandMacroForView(macroToExpand: RsPossibleMacroCall, expandRecursively: Boolean): MacroExpansionViewDetails? {
    val expansions = getMacroExpansions(macroToExpand, expandRecursively) ?: return null
    return MacroExpansionViewDetails(
        macroToExpand,
        getMacroExpansionViewTitle(macroToExpand, expandRecursively),
        expansions
    )
}

private fun getMacroExpansionViewTitle(macroToExpand: RsPossibleMacroCall, expandRecursively: Boolean): String {
    val path = macroToExpand.path?.text
    val name = when (val kind = macroToExpand.kind) {
        is RsPossibleMacroCallKind.MacroCall -> "$path! macro"
        is RsPossibleMacroCallKind.MetaItem -> if (RsProcMacroPsiUtil.canBeCustomDerive(kind.meta)) {
            "#[derive($path)]"
        } else {
            "#[$path]"
        }
    }
    return if (expandRecursively) {
        "Recursive expansion of $name"
    } else {
        "First level expansion of $name"
    }
}

private fun getMacroExpansions(macroToExpand: RsPossibleMacroCall, expandRecursively: Boolean): MacroExpansion? {
    if (macroToExpand.expansion == null) {
        return null
    }

    val expansionText = if (expandRecursively) {
        macroToExpand.expandMacrosRecursively(replaceDollarCrate = true)
    } else {
        macroToExpand.expandMacrosRecursively(depthLimit = 1, replaceDollarCrate = true)
    }

    return parseExpandedTextWithContext(
        macroToExpand.expansionContext,
        // Without `eventSystemEnabled` file reformatting (that will be performed later) is too slow
        RsPsiFactory(macroToExpand.project, markGenerated = false, eventSystemEnabled = true),
        expansionText
    )
}

private fun reformatMacroExpansion(
    macroToExpand: RsPossibleMacroCall,
    expansion: MacroExpansion
): MacroExpansion {
    val file = expansion.file
        .takeIf { it.virtualFile == null }
        // Without `eventSystemEnabled` file reformatting is too slow
        ?: RsPsiFactory(expansion.file.project, eventSystemEnabled = true).createFile(expansion.file.text)

    RsPsiManager.withIgnoredPsiEvents(file) {
        DocumentUtil.writeInRunUndoTransparentAction { formatPsiFile(file) }
    }

    return getExpansionFromExpandedFile(macroToExpand.expansionContext, file)
        ?: error("Can't recover macro expansion after reformat")
}

/** Simple view to show some code. Inspired by [com.intellij.codeInsight.hint.ImplementationViewComponent] */
private class MacroExpansionViewComponent(expansion: MacroExpansion) : JPanel(BorderLayout()) {

    private val editor: EditorEx

    init {
        require(expansion.elements.isNotEmpty()) { "Must be at least one expansion!" }
        val project = expansion.file.project

        editor = project.createReadOnlyEditorWithElements(expansion.elements)
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
