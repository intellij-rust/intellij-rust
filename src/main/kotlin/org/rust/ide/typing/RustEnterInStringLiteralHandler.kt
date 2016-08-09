package org.rust.ide.typing

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.StringEscapesTokenTypes
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.elementType

class RustEnterInStringLiteralHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvanceRef: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): Result {
        // return if this is not a Rust file
        if (file !is RustFile) {
            return Result.Continue
        }

        val caretOffset = caretOffsetRef.get()

        if (!isInStringOrRawStringLiteral(editor, caretOffset)) {
            return Result.Continue
        }

        // commit any document changes, so we'll get latest PSI
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)

        // find the PsiElement at the caret
        val elementAtCaret = file.findElementAt(caretOffset) ?: return Result.Continue

        return when (elementAtCaret.elementType) {
            STRING_LITERAL, BYTE_STRING_LITERAL -> {
                // If we are inside string literal, add trailing '\' just before caret
                var offset = caretOffset
                editor.document.insertString(offset++, "\\")
                caretOffsetRef.set(offset)
                Result.DefaultForceIndent
            }

            RAW_STRING_LITERAL, RAW_BYTE_STRING_LITERAL ->
                Result.DefaultSkipIndent

            else -> Result.Continue
        }
    }

    private fun isInStringOrRawStringLiteral(editor: Editor, offset: Int): Boolean {
        if (offset < 1) return false
        val quotedHandler = RustQuoteHandler()
        val highlighter = (editor as EditorEx).highlighter
        val iterator = highlighter.createIterator(offset - 1)
        return quotedHandler.isDeepInsideLiteral(iterator, offset)
            || iterator.tokenType in StringEscapesTokenTypes.STRING_LITERAL_ESCAPES
    }
}
