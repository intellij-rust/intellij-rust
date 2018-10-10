/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import org.rust.lang.core.psi.RsElementTypes.*

class RsAttributeParenthesisTypedHandler : RsBraceTypedHandler(AttributeParenthesisHandler)

class RsAttributeParenthesisBackspaceHandler : RsBraceBackspaceHandler(AttributeParenthesisHandler)

object AttributeParenthesisHandler : BraceHandler {

    override val opening: BraceKind = BraceKind('(', LPAREN)
    override val closing: BraceKind = BraceKind(')', RPAREN)

    override fun shouldComplete(editor: Editor): Boolean {
        val offset = editor.caretModel.offset
        val lexer = editor.createLexer(offset - 1) ?: return false

        // Don't complete parenthesis inside of an identifier
        if (lexer.tokenType == IDENTIFIER && lexer.end != offset) return false

        // Walk backwards to the nearest meta-attribute start
        while (lexer.start > 0 && !isStartOfMetaAttribute(editor, lexer.start)) {
            // If we hit a "]" on the way, we're outside a meta attribute
            if (lexer.tokenType == RBRACK) return false
            lexer.retreat()
        }
        return isStartOfMetaAttribute(editor, lexer.start)
    }

    override fun calculateBalance(editor: Editor): Int {
        val offset = editor.caretModel.offset
        val iterator = (editor as EditorEx).highlighter.createIterator(offset)

        // Walk backwards to the start of the nearest meta attribute
        while (iterator.start > 0 && !isStartOfMetaAttribute(editor, iterator.start)) {
            iterator.retreat()
        }

        var balance = 0
        while (!iterator.atEnd() && balance >= 0 && iterator.tokenType != RBRACK) {
            when (iterator.tokenType) {
                LPAREN -> balance++
                RPAREN -> balance--
            }
            iterator.advance()
        }
        return balance
    }

    /** Meta attributes begin with "#[" or "#![" */
    private fun isStartOfMetaAttribute(editor: Editor, offset: Int): Boolean {
        val lexer = editor.createLexer(offset) ?: return false

        // Check for "#" token
        if (lexer.tokenType != SHA) return false
        lexer.advance()

        return when (lexer.tokenType) {
            // "#[" -> true
            LBRACK -> true
            EXCL -> {
                lexer.advance()
                // "#![" -> true
                lexer.tokenType == LBRACK
            }
            else -> false
        }
    }
}
