package org.rust.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.SyntaxHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RustKeywordTokenType
import org.rust.lang.core.lexer.RustTokenType
import org.rust.lang.core.lexer.RustLexer
import org.rust.lang.core.lexer.RustTokenElementTypes.*

public class RustHighlighter : SyntaxHighlighterBase() {

    public class Factory : SyntaxHighlighterFactory() {
        override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = RustHighlighter();
    }

    object Colors {

        fun r(id: String, attrKey: TextAttributesKey) =
                TextAttributesKey.createTextAttributesKey(id, attrKey)

        val IDENTIFIER      = r("org.rust.IDENTIFIER",      DefaultLanguageHighlighterColors.IDENTIFIER)

        val CHAR            = r("org.rust.CHAR",            DefaultLanguageHighlighterColors.STRING)
        val STRING          = r("org.rust.STRING",          DefaultLanguageHighlighterColors.STRING)
        val NUMBER          = r("org.rust.NUMBER",          DefaultLanguageHighlighterColors.NUMBER)

        val KEYWORD         = r("org.rust.KEYWORD",         DefaultLanguageHighlighterColors.KEYWORD)

        val BLOCK_COMMENT   = r("org.rust.BLOCK_COMMENT",   DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val EOL_COMMENT     = r("org.rust.EOL_COMMENT",     DefaultLanguageHighlighterColors.LINE_COMMENT)
        val DOC_COMMENT     = r("org.rust.DOC_COMMENT",     DefaultLanguageHighlighterColors.DOC_COMMENT)

        val PARENTHESIS     = r("org.rust.PARENTHESIS",     DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS        = r("org.rust.BRACKETS",        DefaultLanguageHighlighterColors.BRACKETS)
        val BRACES          = r("org.rust.BRACES",          DefaultLanguageHighlighterColors.BRACES)

        val OPERATORS       = r("org.rust.OPERATORS",       DefaultLanguageHighlighterColors.OPERATION_SIGN)

        val SEMICOLON       = r("org.rust.SEMICOLON",       DefaultLanguageHighlighterColors.SEMICOLON)
        val DOT             = r("org.rust.DOT",             DefaultLanguageHighlighterColors.DOT)
        val COMMA           = r("org.rust.COMMA",           DefaultLanguageHighlighterColors.COMMA)
    }

    override fun getHighlightingLexer(): Lexer {
        return RustLexer();
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<out TextAttributesKey> {
        return SyntaxHighlighterBase.pack(map(tokenType))
    }

    private fun map(tokenType: IElementType?): TextAttributesKey? {
        return  if (tokenType is RustKeywordTokenType)
                    Colors.KEYWORD
                else when (tokenType) {

                    IDENTIFIER          -> Colors.IDENTIFIER

                    CHAR_LITERAL        -> Colors.CHAR
                    STRING_LITERAL      -> Colors.STRING
                    INTEGER_LITERAL     -> Colors.NUMBER
                    FLOAT_LITERAL       -> Colors.NUMBER

                    BLOCK_COMMENT       -> Colors.BLOCK_COMMENT
                    EOL_COMMENT         -> Colors.EOL_COMMENT

                    BLOCK_DOC_COMMENT   -> Colors.DOC_COMMENT
                    EOL_DOC_COMMENT     -> Colors.DOC_COMMENT

                    LPAREN, RPAREN      -> Colors.PARENTHESIS
                    LBRACE, RBRACE      -> Colors.BRACES
                    LBRACK, RBRACK      -> Colors.BRACKETS

                    SEMICOLON           -> Colors.SEMICOLON
                    DOT                 -> Colors.DOT
                    COMMA               -> Colors.COMMA

                    else -> null
                }
    }

}
