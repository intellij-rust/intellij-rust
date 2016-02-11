package org.rust.ide.colorscheme

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as DLHC
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey as r

object RustColors {
    val IDENTIFIER      = r("org.rust.IDENTIFIER",      DLHC.IDENTIFIER)

    val LIFETIME        = r("org.rust.LIFETIME",        DLHC.IDENTIFIER)

    val CHAR            = r("org.rust.CHAR",            DLHC.STRING)
    val STRING          = r("org.rust.STRING",          DLHC.STRING)
    val NUMBER          = r("org.rust.NUMBER",          DLHC.NUMBER)

    val KEYWORD         = r("org.rust.KEYWORD",         DLHC.KEYWORD)

    val BLOCK_COMMENT   = r("org.rust.BLOCK_COMMENT",   DLHC.BLOCK_COMMENT)
    val EOL_COMMENT     = r("org.rust.EOL_COMMENT",     DLHC.LINE_COMMENT)
    val DOC_COMMENT     = r("org.rust.DOC_COMMENT",     DLHC.DOC_COMMENT)

    val PARENTHESIS     = r("org.rust.PARENTHESIS",     DLHC.PARENTHESES)
    val BRACKETS        = r("org.rust.BRACKETS",        DLHC.BRACKETS)
    val BRACES          = r("org.rust.BRACES",          DLHC.BRACES)

    val OPERATORS       = r("org.rust.OPERATORS",       DLHC.OPERATION_SIGN)

    val SEMICOLON       = r("org.rust.SEMICOLON",       DLHC.SEMICOLON)
    val DOT             = r("org.rust.DOT",             DLHC.DOT)
    val COMMA           = r("org.rust.COMMA",           DLHC.COMMA)

    val ATTRIBUTE       = r("org.rust.ATTRIBUTE",       DLHC.METADATA)

    val MACRO           = r("org.rust.MACRO",           DLHC.IDENTIFIER)

    val TYPE_PARAMETER  = r("org.rust.TYPE_PARAMETER",  DLHC.IDENTIFIER)

    val MUT_BINDING     = r("org.rust.MUT_BINDING",     DLHC.IDENTIFIER)

    val VALID_STRING_ESCAPE   = r("org.rust.VALID_STRING_ESCAPE",   DLHC.VALID_STRING_ESCAPE)
    val INVALID_STRING_ESCAPE = r("org.rust.INVALID_STRING_ESCAPE", DLHC.INVALID_STRING_ESCAPE)
}
