/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.DeleteProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.editor.impl.TextDrawingCallback
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.ui.ButtonlessScrollBarUI
import org.rust.ide.highlight.RsHighlighter
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.mapRangeFromExpansionToCallBodyStrict
import org.rust.lang.core.psi.RsMacroArgument
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.expansion
import org.rust.lang.core.psi.ext.startOffset
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JScrollPane

class RsMacroCallSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean = e.ancestorStrict<RsMacroArgument>() != null

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val elementInExpansion = e.findExpansionElements()?.firstOrNull() ?: return null
        val offsetInExpansion = elementInExpansion.startOffset + (cursorOffset - e.startOffset)
        val macroCall = elementInExpansion.findMacroCallExpandedFromNonRecursive() as? RsMacroCall ?: return null
        val expansion = macroCall.expansion ?: return null // impossible?
        val expansionText = expansion.file.text

        // A real EditorImpl can't be created outside of EDT (`select` is called outside of EDT since 2020.3)
        val expansionEditor = FakeEditorEx(e.project, expansionText, editor)

        val ranges = mutableListOf<TextRange>()
        SelectWordUtil.processRanges(elementInExpansion, expansionText, offsetInExpansion, expansionEditor) {
            ranges.add(it)
            false // Continue processing (do not believe `Processor`'s docs)
        }
        return ranges.mapNotNull { macroCall.mapRangeFromExpansionToCallBodyStrict(it) }
    }
}


// EditorEx and Highlighter is needed for InjectedFileReferenceSelectioner, ImaginaryEditor is not enough.
private class FakeEditorEx(
    project: Project,
    text: String,
    private val editor: Editor
) : ImaginaryEditor(project, DocumentImpl(text)), EditorEx {

    private val highlighter = LexerEditorHighlighter(RsHighlighter(), editor.colorsScheme)
        .apply { setText(text) }

    override fun getDocument(): DocumentEx = super.getDocument() as DocumentEx
    override fun getSettings(): EditorSettings = editor.settings
    override fun getHighlighter(): EditorHighlighter = highlighter
    override fun getMarkupModel(): MarkupModelEx { throw notImplemented() }
    override fun getFoldingModel(): FoldingModelEx { throw notImplemented() }
    override fun getScrollingModel(): ScrollingModelEx { throw notImplemented() }
    override fun getSoftWrapModel(): SoftWrapModelEx { throw notImplemented() }
    override fun getFilteredDocumentMarkupModel(): MarkupModelEx { throw notImplemented() }
    override fun getGutterComponentEx(): EditorGutterComponentEx { throw notImplemented() }
    override fun getPermanentHeaderComponent(): JComponent { throw notImplemented() }
    override fun setViewer(isViewer: Boolean) { throw notImplemented() }
    override fun setPermanentHeaderComponent(component: JComponent?) { throw notImplemented() }
    override fun setHighlighter(highlighter: EditorHighlighter) { throw notImplemented() }
    override fun setColorsScheme(scheme: EditorColorsScheme) { throw notImplemented() }
    override fun setInsertMode(`val`: Boolean) { throw notImplemented() }
    override fun setColumnMode(`val`: Boolean) { throw notImplemented() }
    override fun setVerticalScrollbarOrientation(type: Int) { throw notImplemented() }
    override fun getVerticalScrollbarOrientation(): Int { throw notImplemented() }
    override fun setVerticalScrollbarVisible(b: Boolean) { throw notImplemented() }
    override fun setHorizontalScrollbarVisible(b: Boolean) { throw notImplemented() }
    override fun getCutProvider(): CutProvider { throw notImplemented() }
    override fun getCopyProvider(): CopyProvider { throw notImplemented() }
    override fun getPasteProvider(): PasteProvider { throw notImplemented() }
    override fun getDeleteProvider(): DeleteProvider { throw notImplemented() }
    override fun repaint(startOffset: Int, endOffset: Int) { throw notImplemented() }
    override fun reinitSettings() { throw notImplemented() }
    override fun addPropertyChangeListener(listener: PropertyChangeListener, parentDisposable: Disposable) { throw notImplemented() }
    override fun addPropertyChangeListener(listener: PropertyChangeListener) { throw notImplemented() }
    override fun removePropertyChangeListener(listener: PropertyChangeListener) { throw notImplemented() }
    override fun getMaxWidthInRange(startOffset: Int, endOffset: Int): Int { throw notImplemented() }
    override fun setCaretVisible(b: Boolean): Boolean { throw notImplemented() }
    override fun setCaretEnabled(enabled: Boolean): Boolean { throw notImplemented() }
    override fun addFocusListener(listener: FocusChangeListener) { throw notImplemented() }
    override fun addFocusListener(listener: FocusChangeListener, parentDisposable: Disposable) { throw notImplemented() }
    override fun setOneLineMode(b: Boolean) { throw notImplemented() }
    override fun getScrollPane(): JScrollPane { throw notImplemented() }
    override fun isRendererMode(): Boolean { throw notImplemented() }
    override fun setRendererMode(isRendererMode: Boolean) { throw notImplemented() }
    override fun setFile(vFile: VirtualFile?) { throw notImplemented() }
    override fun getDataContext(): DataContext { throw notImplemented() }
    override fun processKeyTyped(e: KeyEvent): Boolean { throw notImplemented() }
    override fun setFontSize(fontSize: Int) { throw notImplemented() }
    override fun getBackgroundColor(): Color { throw notImplemented() }
    override fun setBackgroundColor(color: Color?) { throw notImplemented() }
    override fun getContentSize(): Dimension { throw notImplemented() }
    override fun isEmbeddedIntoDialogWrapper(): Boolean { throw notImplemented() }
    override fun setEmbeddedIntoDialogWrapper(b: Boolean) { throw notImplemented() }
    override fun getVirtualFile(): VirtualFile { throw notImplemented() }
    override fun getTextDrawingCallback(): TextDrawingCallback { throw notImplemented() }
    override fun createBoundColorSchemeDelegate(customGlobalScheme: EditorColorsScheme?): EditorColorsScheme { throw notImplemented() }
    override fun setPlaceholder(text: CharSequence?) { throw notImplemented() }
    override fun setPlaceholderAttributes(attributes: TextAttributes?) { throw notImplemented() }
    override fun setShowPlaceholderWhenFocused(show: Boolean) { throw notImplemented() }
    override fun isStickySelection(): Boolean { throw notImplemented() }
    override fun setStickySelection(enable: Boolean) { throw notImplemented() }
    override fun getPrefixTextWidthInPixels(): Int { throw notImplemented() }
    override fun setPrefixTextAndAttributes(prefixText: String?, attributes: TextAttributes?) { throw notImplemented() }
    override fun isPurePaintingMode(): Boolean { throw notImplemented() }
    override fun setPurePaintingMode(enabled: Boolean) { throw notImplemented() }
    override fun registerLineExtensionPainter(lineExtensionPainter: LineExtensionPainter) { throw notImplemented() }
    override fun registerScrollBarRepaintCallback(callback: ButtonlessScrollBarUI.ScrollbarRepaintCallback?) { throw notImplemented() }
    override fun getExpectedCaretOffset(): Int { throw notImplemented() }
    override fun setContextMenuGroupId(groupId: String?) { throw notImplemented() }
    override fun getContextMenuGroupId(): String? { throw notImplemented() }
    override fun installPopupHandler(popupHandler: EditorPopupHandler) { throw notImplemented() }
    override fun uninstallPopupHandler(popupHandler: EditorPopupHandler) { throw notImplemented() }
    override fun setCustomCursor(requestor: Any, cursor: Cursor?) { throw notImplemented() }
}
