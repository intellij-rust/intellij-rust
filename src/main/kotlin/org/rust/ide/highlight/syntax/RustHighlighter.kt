package org.rust.ide.highlight.syntax

import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.StringEscapesTokenTypes.*
import com.intellij.psi.tree.IElementType
import org.rust.ide.colors.RustColor
import org.rust.lang.core.lexer.RustHighlightingLexer
import org.rust.lang.core.psi.RustKeywordTokenType
import org.rust.lang.core.psi.RustTokenElementTypes.*

class RustHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer() = RustHighlightingLexer()

    override fun getTokenHighlights(tokenType: IElementType?) = pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): RustColor? = when (tokenType) {
            is RustKeywordTokenType        -> RustColor.KEYWORD

            IDENTIFIER                     -> RustColor.IDENTIFIER

            LIFETIME                       -> RustColor.LIFETIME

            CHAR_LITERAL                   -> RustColor.CHAR
            BYTE_LITERAL                   -> RustColor.CHAR
            STRING_LITERAL                 -> RustColor.STRING
            BYTE_STRING_LITERAL            -> RustColor.STRING
            RAW_STRING_LITERAL             -> RustColor.STRING
            RAW_BYTE_STRING_LITERAL        -> RustColor.STRING
            INTEGER_LITERAL                -> RustColor.NUMBER
            FLOAT_LITERAL                  -> RustColor.NUMBER

            BLOCK_COMMENT                  -> RustColor.BLOCK_COMMENT
            EOL_COMMENT                    -> RustColor.EOL_COMMENT

            INNER_DOC_COMMENT              -> RustColor.DOC_COMMENT
            OUTER_DOC_COMMENT              -> RustColor.DOC_COMMENT

            LPAREN, RPAREN                 -> RustColor.PARENTHESIS
            LBRACE, RBRACE                 -> RustColor.BRACES
            LBRACK, RBRACK                 -> RustColor.BRACKETS

            SEMICOLON                      -> RustColor.SEMICOLON
            DOT                            -> RustColor.DOT
            COMMA                          -> RustColor.COMMA

            VALID_STRING_ESCAPE_TOKEN      -> RustColor.VALID_STRING_ESCAPE
            INVALID_CHARACTER_ESCAPE_TOKEN -> RustColor.INVALID_STRING_ESCAPE
            INVALID_UNICODE_ESCAPE_TOKEN   -> RustColor.INVALID_STRING_ESCAPE

            else                           -> null
        }
    }
}
