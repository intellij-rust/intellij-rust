package org.rust.ide.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

/**
 * See [RustColorSettingsPage] and [org.rust.ide.highlight.syntax.RustHighlighter]
 */
enum class RustColor(val humanName: String, val default: TextAttributesKey) {
    IDENTIFIER            ("Identifier",                  Default.IDENTIFIER),
    FUNCTION_DECLARATION  ("Function declaration",        Default.FUNCTION_DECLARATION),
    INSTANCE_METHOD       ("Instance method declaration", Default.INSTANCE_METHOD),
    STATIC_METHOD         ("Static method declaration",   Default.STATIC_METHOD),

    LIFETIME              ("Lifetime",                    Default.IDENTIFIER),

    CHAR                  ("Char",                        Default.STRING),
    STRING                ("String",                      Default.STRING),
    NUMBER                ("Number",                      Default.NUMBER),

    PRIMITIVE_TYPE        ("Primitive Type",              Default.KEYWORD),

    CRATE                 ("Crate",                       Default.IDENTIFIER),
    STRUCT                ("Struct",                      Default.CLASS_NAME),
    TRAIT                 ("Trait",                       Default.INTERFACE_NAME),
    MODULE                ("Module",                      Default.IDENTIFIER),
    ENUM                  ("Enum",                        Default.CLASS_NAME),
    ENUM_VARIANT          ("Enum Variant",                Default.STATIC_FIELD),

    FIELD                 ("Field",                       Default.INSTANCE_FIELD),

    KEYWORD               ("Keyword",                     Default.KEYWORD),

    BLOCK_COMMENT         ("Block comment",               Default.BLOCK_COMMENT),
    EOL_COMMENT           ("Line comment",                Default.LINE_COMMENT),
    DOC_COMMENT           ("Documentation comment",       Default.DOC_COMMENT),

    PARENTHESIS           ("Parenthesis",                 Default.PARENTHESES),
    BRACKETS              ("Brackets",                    Default.BRACKETS),
    BRACES                ("Braces",                      Default.BRACES),

    OPERATORS             ("Operator sign",               Default.OPERATION_SIGN),

    SEMICOLON             ("Semicolon",                   Default.SEMICOLON),
    DOT                   ("Dot",                         Default.DOT),
    COMMA                 ("Comma",                       Default.COMMA),

    ATTRIBUTE             ("Attribute",                   Default.METADATA),

    MACRO                 ("Macro",                       Default.IDENTIFIER),

    TYPE_PARAMETER        ("Type parameter",              Default.IDENTIFIER),

    MUT_BINDING           ("Mutable binding",             Default.IDENTIFIER),

    VALID_STRING_ESCAPE   ("Valid escape sequence",       Default.VALID_STRING_ESCAPE),
    INVALID_STRING_ESCAPE ("Invalid escape sequence",     Default.INVALID_STRING_ESCAPE),
    ;

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.rust.${this.name}", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}

