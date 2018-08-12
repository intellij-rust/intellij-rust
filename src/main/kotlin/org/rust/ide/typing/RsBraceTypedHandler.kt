/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.RsFile

data class TypingPairAtom(val char: Char, val token: IElementType)

interface TypingPairHandler {
    val opening: TypingPairAtom
    val closing: TypingPairAtom
    fun shouldComplete(editor: Editor): Boolean
    fun calculateBalance(editor: Editor): Int

    fun Editor.createLexer(offset: Int): HighlighterIterator? {
        if (!isValidOffset(offset, document.charsSequence)) return null
        val lexer = (this as EditorEx).highlighter.createIterator(offset)
        if (lexer.atEnd()) return null
        return lexer
    }
}

abstract class RsBraceTypedHandler(private val handler: TypingPairHandler) : TypedHandlerDelegate() {

    private var openingTyped = false

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file !is RsFile) return Result.CONTINUE

        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            when (c) {
                handler.opening.char -> openingTyped = handler.shouldComplete(editor)
                handler.closing.char -> {
                    val lexer = editor.createLexer(editor.caretModel.offset) ?: return Result.CONTINUE
                    val tokenType = lexer.tokenType
                    if (tokenType == handler.closing.token && handler.calculateBalance(editor) == 0) {
                        EditorModificationUtil.moveCaretRelatively(editor, 1)
                        return Result.STOP
                    }
                }
            }
        }

        return Result.CONTINUE
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is RsFile) return Result.CONTINUE

        if (openingTyped) {
            openingTyped = false
            val balance = handler.calculateBalance(editor)
            if (balance == 1) {
                val offset = editor.caretModel.offset
                editor.document.insertString(offset, handler.closing.char.toString())
            }
        }

        return super.charTyped(c, project, editor, file)
    }
}

abstract class RsBraceBackspaceHandler(private val handler: TypingPairHandler) : RsEnableableBackspaceHandlerDelegate() {

    override fun deleting(c: Char, file: PsiFile, editor: Editor): Boolean {
        if (c == handler.opening.char && file is RsFile) {
            val offset = editor.caretModel.offset
            val iterator = (editor as EditorEx).highlighter.createIterator(offset)
            return iterator.tokenType == handler.closing.token
        }
        return false
    }

    override fun deleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        val balance = handler.calculateBalance(editor)
        if (balance < 0) {
            val offset = editor.caretModel.offset
            editor.document.deleteString(offset, offset + 1)
            return true
        }
        return true
    }
}

private fun Editor.createLexer(offset: Int): HighlighterIterator? {
    if (!isValidOffset(offset, document.charsSequence)) return null
    val lexer = (this as EditorEx).highlighter.createIterator(offset)
    if (lexer.atEnd()) return null
    return lexer
}
