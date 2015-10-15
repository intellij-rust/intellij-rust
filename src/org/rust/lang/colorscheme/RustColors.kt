package org.rust.lang.colorscheme

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object RustColors {
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

    val ATTRIBUTE       = r("org.rust.ATTRIBUTE",    DefaultLanguageHighlighterColors.METADATA)
}