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
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.tokenSetOf

private val GENERIC_NAMED_ENTITY_KEYWORDS = tokenSetOf(FN, STRUCT, ENUM, TRAIT, TYPE_KW)

private val INVALID_INSIDE_TOKENS = tokenSetOf(LBRACE, RBRACE, SEMICOLON)

class RsAngleBraceTypedHandler : TypedHandlerDelegate() {

    private var rsLTTyped = false

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file !is RsFile) return Result.CONTINUE

        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            when (c) {
                '<' -> rsLTTyped = isStartOfGenericBraces(editor)
                '>' -> {
                    val lexer = editor.createLexer(editor.caretModel.offset)
                        ?: return Result.CONTINUE
                    val tokenType = lexer.tokenType
                    if (tokenType == GT && calculateBalance(editor) == 0) {
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

        if (rsLTTyped) {
            rsLTTyped = false
            val balance = calculateBalance(editor)
            if (balance == 1) {
                val offset = editor.caretModel.offset
                editor.document.insertString(offset, ">")
            }
            return Result.STOP

        }
        return Result.CONTINUE
    }
}

class RsAngleBraceBackspaceHandler : RsEnableableBackspaceHandlerDelegate() {

    override fun deleting(c: Char, file: PsiFile, editor: Editor): Boolean {
        if (c == '<' && file is RsFile) {
            val offset = editor.caretModel.offset
            val iterator = (editor as EditorEx).highlighter.createIterator(offset)
            return iterator.tokenType == GT
        }
        return false
    }

    override fun deleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        val balance = calculateBalance(editor)
        if (balance < 0) {
            val offset = editor.caretModel.offset
            editor.document.deleteString(offset, offset + 1)
            return true
        }
        return true
    }
}

private fun isStartOfGenericBraces(editor: Editor): Boolean {
    val offset = editor.caretModel.offset
    val lexer = editor.createLexer(offset - 1)
        ?: return false

    return when (lexer.tokenType) {
        // manual function type specification
        COLONCOLON -> true
        // generic implementation block
        IMPL -> true
        IDENTIFIER -> {
            // don't complete angle braces inside identifier
            if (lexer.end != offset) return false
            // it considers that typical case is only one whitespace character
            // between keyword (fn, enum, etc.) and identifier
            if (lexer.start > 1) {
                lexer.retreat()
                lexer.retreat()
                if (lexer.tokenType in GENERIC_NAMED_ENTITY_KEYWORDS) return true
                lexer.advance()
                lexer.advance()
            }
            isTypeLikeIdentifier(offset, editor, lexer)
        }
        else -> false
    }
}

private fun Editor.createLexer(offset: Int): HighlighterIterator? {
    if (!isValidOffset(offset, document.charsSequence)) return null
    val lexer = (this as EditorEx).highlighter.createIterator(offset)
    if (lexer.atEnd()) return null
    return lexer
}

private fun isTypeLikeIdentifier(offset: Int, editor: Editor, iterator: HighlighterIterator): Boolean {
    if (iterator.end != offset) return false
    val chars = editor.document.charsSequence
    if (!Character.isUpperCase(chars[iterator.start])) return false
    if (iterator.end == iterator.start + 1) return true
    return (iterator.start + 1 until iterator.end).any { Character.isLowerCase(chars[it]) }
}

private fun calculateBalance(editor: Editor): Int {
    val offset = editor.caretModel.offset
    val iterator = (editor as EditorEx).highlighter.createIterator(offset)
    while (iterator.start > 0 && iterator.tokenType !in INVALID_INSIDE_TOKENS) {
        iterator.retreat()
    }

    if (iterator.tokenType in INVALID_INSIDE_TOKENS) {
        iterator.advance()
    }

    var balance = 0
    while (!iterator.atEnd() && balance >= 0 && iterator.tokenType !in INVALID_INSIDE_TOKENS) {
        when (iterator.tokenType) {
            LT -> balance++
            GT -> balance--
        }
        iterator.advance()
    }

    return balance
}
