/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf

private val GENERIC_NAMED_ENTITY_KEYWORDS = tokenSetOf(FN, STRUCT, ENUM, TRAIT, TYPE_KW)

private val INVALID_INSIDE_TOKENS = tokenSetOf(LBRACE, RBRACE, SEMICOLON)

class RsAngleBraceTypedHandler : RsBraceTypedHandler(AngleBraceHandler)

class RsAngleBraceBackspaceHandler : RsBraceBackspaceHandler(AngleBraceHandler)

object AngleBraceHandler : BraceHandler {

    override val opening: BraceKind = BraceKind('<', LT)
    override val closing: BraceKind = BraceKind('>', GT)

    override fun shouldComplete(editor: Editor): Boolean {
        val offset = editor.caretModel.offset
        val lexer = editor.createLexer(offset - 1) ?: return false

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

    override fun calculateBalance(editor: Editor): Int {
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

    private fun isTypeLikeIdentifier(offset: Int, editor: Editor, iterator: HighlighterIterator): Boolean {
        if (iterator.end != offset) return false
        val chars = editor.document.charsSequence
        if (!Character.isUpperCase(chars[iterator.start])) return false
        if (iterator.end == iterator.start + 1) return true
        return (iterator.start + 1 until iterator.end).any { Character.isLowerCase(chars[it]) }
    }
}
