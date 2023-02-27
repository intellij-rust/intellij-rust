/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.util.macros

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.EmptySoftWrapModel
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.psi.PsiFile
import kotlin.math.min

class RsIntentionInsideMacroExpansionEditor(
    val psiFileCopy: PsiFile,
    val originalFile: PsiFile,
    val originalEditor: Editor,
    val initialMappedOffset: Int?,
    val context: RsIntentionInsideMacroExpansionContext?,
) : ImaginaryEditor(psiFileCopy.project, psiFileCopy.viewProvider.document!!) {

    init {
        if (initialMappedOffset != null) {
            caretModel.moveToOffset(initialMappedOffset)
        }
    }

    override fun notImplemented(): RuntimeException = IntentionInsideMacroExpansionEditorUnsupportedOperationException()

    override fun isViewer(): Boolean = true

    override fun isOneLineMode(): Boolean = false

    override fun getSettings(): EditorSettings {
        return originalEditor.settings
    }

    override fun logicalPositionToOffset(pos: LogicalPosition): Int {
        val document = document
        val lineStart = document.getLineStartOffset(pos.line)
        val lineEnd = document.getLineEndOffset(pos.line)
        return min(lineEnd, lineStart + pos.column)
    }

    override fun logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition {
        // No folding support: logicalPos is always the same as visual pos
        return VisualPosition(logicalPos.line, logicalPos.column)
    }

    override fun visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition {
        return LogicalPosition(visiblePos.line, visiblePos.column)
    }

    override fun offsetToLogicalPosition(offset: Int): LogicalPosition {
        val clamped = offset.coerceIn(0, document.textLength)
        val document = document
        val line = document.getLineNumber(clamped)
        val col = clamped - document.getLineStartOffset(line)
        return LogicalPosition(line, col)
    }

    override fun getSoftWrapModel(): SoftWrapModel = EmptySoftWrapModel()
}

class IntentionInsideMacroExpansionEditorUnsupportedOperationException
    : UnsupportedOperationException("It's unexpected to invoke this method on macro expansion fake editor")
