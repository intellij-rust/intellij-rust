package org.rust.ide.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.StringEscapesTokenTypes.*
import com.intellij.psi.tree.IElementType
import org.rust.ide.colors.RsColor
import org.rust.lang.core.lexer.RsHighlightingLexer
import org.rust.lang.core.psi.RsKeywordTokenType
import org.rust.lang.core.psi.RsOperatorTokenType
import org.rust.lang.core.psi.RsTokenElementTypes.*
import org.rust.lang.doc.psi.RsDocElementTypes.*

class RsHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer() = RsHighlightingLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): RsColor? = when (tokenType) {
            IDENTIFIER -> RsColor.IDENTIFIER
            UNDERSCORE -> RsColor.IDENTIFIER

            LIFETIME -> RsColor.LIFETIME

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

            DOC_TEXT -> RsColor.DOC_COMMENT
            DOC_HEADING -> RsColor.DOC_HEADING
            DOC_INLINE_LINK -> RsColor.DOC_LINK
            DOC_REF_LINK -> RsColor.DOC_LINK
            DOC_LINK_REF_DEF -> RsColor.DOC_LINK
            DOC_CODE_SPAN -> RsColor.DOC_CODE
            DOC_CODE_FENCE -> RsColor.DOC_CODE

            LPAREN, RPAREN -> RsColor.PARENTHESIS
            LBRACE, RBRACE -> RsColor.BRACES
            LBRACK, RBRACK -> RsColor.BRACKETS

            SEMICOLON -> RsColor.SEMICOLON
            DOT -> RsColor.DOT
            COMMA -> RsColor.COMMA

            VALID_STRING_ESCAPE_TOKEN -> RsColor.VALID_STRING_ESCAPE
            INVALID_CHARACTER_ESCAPE_TOKEN -> RsColor.INVALID_STRING_ESCAPE
            INVALID_UNICODE_ESCAPE_TOKEN -> RsColor.INVALID_STRING_ESCAPE

            is RsKeywordTokenType -> RsColor.KEYWORD
            is RsOperatorTokenType -> RsColor.OPERATORS

            else -> null
        }
    }
}
