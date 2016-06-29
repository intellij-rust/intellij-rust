package org.rust.ide.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

/**
 * See [RustColorSettingsPage] and [org.rust.ide.highlight.syntax.RustHighlighter]
 */
enum class RustColor(val humanName: String, externalName: String, fallback: TextAttributesKey) {
    IDENTIFIER            ("Identifier", "org.rust.IDENTIFIER", Default.IDENTIFIER),
    FUNCTION_DECLARATION  ("Function declaration", "org.rust.FUNCTION_DECLARATION", Default.FUNCTION_DECLARATION),
    INSTANCE_METHOD       ("Instance method declaration", "org.rust.INSTANCE_METHOD", Default.INSTANCE_METHOD),
    STATIC_METHOD         ("Static method declaration", "org.rust.STATIC_METHOD", Default.STATIC_METHOD),

    LIFETIME              ("Lifetime", "org.rust.LIFETIME", Default.IDENTIFIER),

    CHAR                  ("Char", "org.rust.CHAR", Default.STRING),
    STRING                ("String", "org.rust.STRING", Default.STRING),
    NUMBER                ("Number", "org.rust.NUMBER", Default.NUMBER),

    KEYWORD               ("Keyword", "org.rust.KEYWORD", Default.KEYWORD),

    BLOCK_COMMENT         ("Block comment", "org.rust.BLOCK_COMMENT", Default.BLOCK_COMMENT),
    EOL_COMMENT           ("Line comment", "org.rust.EOL_COMMENT", Default.LINE_COMMENT),

    DOC_COMMENT           ("Rustdoc comment", "org.rust.DOC_COMMENT", Default.DOC_COMMENT),
    DOC_HEADING           ("Rustdoc heading", "org.rust.DOC_HEADING", Default.DOC_COMMENT_TAG),
    DOC_LINK              ("Rustdoc link", "org.rust.DOC_LINK", Default.DOC_COMMENT_TAG_VALUE),
    DOC_CODE              ("Rustdoc code", "org.rust.DOC_CODE", Default.DOC_COMMENT_MARKUP),

    PARENTHESIS           ("Parenthesis", "org.rust.PARENTHESIS", Default.PARENTHESES),
    BRACKETS              ("Brackets", "org.rust.BRACKETS", Default.BRACKETS),
    BRACES                ("Braces", "org.rust.BRACES", Default.BRACES),

    OPERATORS             ("Operator sign", "org.rust.OPERATORS", Default.OPERATION_SIGN),

    SEMICOLON             ("Semicolon", "org.rust.SEMICOLON", Default.SEMICOLON),
    DOT                   ("Dot", "org.rust.DOT", Default.DOT),
    COMMA                 ("Comma", "org.rust.COMMA", Default.COMMA),

    ATTRIBUTE             ("Attribute", "org.rust.ATTRIBUTE", Default.METADATA),

    MACRO                 ("Macro", "org.rust.MACRO", Default.IDENTIFIER),

    TYPE_PARAMETER        ("Type parameter", "org.rust.TYPE_PARAMETER", Default.IDENTIFIER),

    MUT_BINDING           ("Mutable binding", "org.rust.MUT_BINDING", Default.IDENTIFIER),

    VALID_STRING_ESCAPE   ("Valid escape sequence", "org.rust.VALID_STRING_ESCAPE", Default.VALID_STRING_ESCAPE),
    INVALID_STRING_ESCAPE ("Invalid excape sequence", "org.rust.INVALID_STRING_ESCAPE", Default.INVALID_STRING_ESCAPE);

    val textAttributesKey = TextAttributesKey.createTextAttributesKey(externalName, fallback)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}

