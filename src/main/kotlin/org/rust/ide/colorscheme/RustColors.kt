package org.rust.ide.colorscheme

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey as r

object RustColors {
    val IDENTIFIER      = r("org.rust.IDENTIFIER",      Default.IDENTIFIER)

    val LIFETIME        = r("org.rust.LIFETIME",        Default.IDENTIFIER)

    val CHAR            = r("org.rust.CHAR",            Default.STRING)
    val STRING          = r("org.rust.STRING",          Default.STRING)
    val NUMBER          = r("org.rust.NUMBER",          Default.NUMBER)

    val KEYWORD         = r("org.rust.KEYWORD",         Default.KEYWORD)

    val BLOCK_COMMENT   = r("org.rust.BLOCK_COMMENT",   Default.BLOCK_COMMENT)
    val EOL_COMMENT     = r("org.rust.EOL_COMMENT",     Default.LINE_COMMENT)
    val DOC_COMMENT     = r("org.rust.DOC_COMMENT",     Default.DOC_COMMENT)

    val PARENTHESIS     = r("org.rust.PARENTHESIS",     Default.PARENTHESES)
    val BRACKETS        = r("org.rust.BRACKETS",        Default.BRACKETS)
    val BRACES          = r("org.rust.BRACES",          Default.BRACES)

    val OPERATORS       = r("org.rust.OPERATORS",       Default.OPERATION_SIGN)

    val SEMICOLON       = r("org.rust.SEMICOLON",       Default.SEMICOLON)
    val DOT             = r("org.rust.DOT",             Default.DOT)
    val COMMA           = r("org.rust.COMMA",           Default.COMMA)

    val ATTRIBUTE       = r("org.rust.ATTRIBUTE",       Default.METADATA)

    val MACRO           = r("org.rust.MACRO",           Default.IDENTIFIER)

    val TYPE_PARAMETER  = r("org.rust.TYPE_PARAMETER",  Default.IDENTIFIER)

    val MUT_BINDING     = r("org.rust.MUT_BINDING",     Default.IDENTIFIER)

    val VALID_STRING_ESCAPE   = r("org.rust.VALID_STRING_ESCAPE",   Default.VALID_STRING_ESCAPE)
    val INVALID_STRING_ESCAPE = r("org.rust.INVALID_STRING_ESCAPE", Default.INVALID_STRING_ESCAPE)
}
