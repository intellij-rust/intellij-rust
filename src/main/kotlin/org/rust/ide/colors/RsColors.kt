/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

/**
 * See [RsColorSettingsPage] and [org.rust.ide.highlight.RsHighlighter]
 */
enum class RsColor(humanName: String, val default: TextAttributesKey) {
    IDENTIFIER("Identifier", Default.IDENTIFIER),
    FUNCTION("Function", Default.FUNCTION_DECLARATION),
    METHOD("Method", Default.INSTANCE_METHOD),
    ASSOC_FUNCTION("Associated function", Default.STATIC_METHOD),
    PARAMETER("Parameter", Default.PARAMETER),
    MUT_PARAMETER("Mutable parameter", Default.PARAMETER),
    SELF_PARAMETER("Self parameter", Default.KEYWORD),
    Q_OPERATOR("? operator", Default.KEYWORD),

    LIFETIME("Lifetime", Default.IDENTIFIER),

    CHAR("Char", Default.STRING),
    STRING("String", Default.STRING),
    NUMBER("Number", Default.NUMBER),

    PRIMITIVE_TYPE("Primitive type", Default.KEYWORD),

    CRATE("Crate", Default.IDENTIFIER),
    STRUCT("Struct", Default.CLASS_NAME),
    TRAIT("Trait", Default.INTERFACE_NAME),
    MODULE("Module", Default.IDENTIFIER),
    ENUM("Enum", Default.CLASS_NAME),
    ENUM_VARIANT("Enum variant", Default.STATIC_FIELD),
    TYPE_ALIAS("Type alias", Default.CLASS_NAME),

    FIELD("Field", Default.INSTANCE_FIELD),

    KEYWORD("Keyword", Default.KEYWORD),

    BLOCK_COMMENT("Block comment", Default.BLOCK_COMMENT),
    EOL_COMMENT("Line comment", Default.LINE_COMMENT),

    DOC_COMMENT("Rustdoc comment", Default.DOC_COMMENT),
    DOC_HEADING("Rustdoc heading", Default.DOC_COMMENT_TAG),
    DOC_LINK("Rustdoc link", Default.DOC_COMMENT_TAG_VALUE),
    DOC_CODE("Rustdoc code", Default.DOC_COMMENT_MARKUP),

    PARENTHESIS("Parenthesis", Default.PARENTHESES),
    BRACKETS("Brackets", Default.BRACKETS),
    BRACES("Braces", Default.BRACES),

    OPERATORS("Operator sign", Default.OPERATION_SIGN),

    SEMICOLON("Semicolon", Default.SEMICOLON),
    DOT("Dot", Default.DOT),
    COMMA("Comma", Default.COMMA),

    ATTRIBUTE("Attribute", Default.METADATA),

    MACRO("Macro", Default.IDENTIFIER),

    TYPE_PARAMETER("Type parameter", Default.IDENTIFIER),

    MUT_BINDING("Mutable binding", Default.IDENTIFIER),

    VALID_STRING_ESCAPE("Valid escape sequence", Default.VALID_STRING_ESCAPE),
    INVALID_STRING_ESCAPE("Invalid escape sequence", Default.INVALID_STRING_ESCAPE),
    ;

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.rust.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}

