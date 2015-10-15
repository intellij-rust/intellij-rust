package org.rust.lang.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import org.rust.lang.colorscheme.RustColors
import org.rust.lang.core.lexer.RustKeywordTokenType
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.lexer.RustTokenElementTypes.*

public class RustHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer {
        return RustLexer();
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<out TextAttributesKey> {
        return SyntaxHighlighterBase.pack(map(tokenType))
    }

    private fun map(tokenType: IElementType?): TextAttributesKey? {
        return if (tokenType is RustKeywordTokenType)
            RustColors.KEYWORD
        else when (tokenType) {

            IDENTIFIER -> RustColors.IDENTIFIER

            CHAR_LITERAL -> RustColors.CHAR
            BYTE_LITERAL -> RustColors.CHAR
            STRING_LITERAL -> RustColors.STRING
            BYTE_STRING_LITERAL -> RustColors.STRING
            INTEGER_LITERAL -> RustColors.NUMBER
            FLOAT_LITERAL -> RustColors.NUMBER

            BLOCK_COMMENT -> RustColors.BLOCK_COMMENT
            EOL_COMMENT -> RustColors.EOL_COMMENT

            INNER_DOC_COMMENT -> RustColors.DOC_COMMENT
            OUTER_DOC_COMMENT -> RustColors.DOC_COMMENT

            LPAREN, RPAREN -> RustColors.PARENTHESIS
            LBRACE, RBRACE -> RustColors.BRACES
            LBRACK, RBRACK -> RustColors.BRACKETS

            SEMICOLON -> RustColors.SEMICOLON
            DOT -> RustColors.DOT
            COMMA -> RustColors.COMMA

            else -> null
        }
    }

}
