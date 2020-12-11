/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.StringEscapesTokenTypes.*
import com.intellij.psi.tree.IElementType
import org.rust.ide.colors.RsColor
import org.rust.lang.core.lexer.RsHighlightingLexer
import org.rust.lang.core.parser.RustParserDefinition.Companion.BLOCK_COMMENT
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
import org.rust.lang.core.psi.RS_DOC_COMMENTS
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RS_OPERATORS
import org.rust.lang.core.psi.RsElementTypes.*

class RsHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer() = RsHighlightingLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType): RsColor? = when (tokenType) {
            IDENTIFIER -> RsColor.IDENTIFIER
            UNDERSCORE -> RsColor.IDENTIFIER

            QUOTE_IDENTIFIER -> RsColor.LIFETIME

            CHAR_LITERAL -> RsColor.CHAR
            BYTE_LITERAL -> RsColor.CHAR
            STRING_LITERAL -> RsColor.STRING
            BYTE_STRING_LITERAL -> RsColor.STRING
            RAW_STRING_LITERAL -> RsColor.STRING
            RAW_BYTE_STRING_LITERAL -> RsColor.STRING
            INTEGER_LITERAL -> RsColor.NUMBER
            FLOAT_LITERAL -> RsColor.NUMBER

            BLOCK_COMMENT -> RsColor.BLOCK_COMMENT
            EOL_COMMENT -> RsColor.EOL_COMMENT

            in RS_DOC_COMMENTS -> RsColor.DOC_COMMENT

            LPAREN, RPAREN -> RsColor.PARENTHESES
            LBRACE, RBRACE -> RsColor.BRACES
            LBRACK, RBRACK -> RsColor.BRACKETS

            SEMICOLON -> RsColor.SEMICOLON
            DOT -> RsColor.DOT
            COMMA -> RsColor.COMMA

            VALID_STRING_ESCAPE_TOKEN -> RsColor.VALID_STRING_ESCAPE
            INVALID_CHARACTER_ESCAPE_TOKEN -> RsColor.INVALID_STRING_ESCAPE
            INVALID_UNICODE_ESCAPE_TOKEN -> RsColor.INVALID_STRING_ESCAPE

            UNSAFE -> RsColor.KEYWORD_UNSAFE
            in RS_KEYWORDS, BOOL_LITERAL -> RsColor.KEYWORD
            in RS_OPERATORS -> RsColor.OPERATORS

            else -> null
        }
    }
}
