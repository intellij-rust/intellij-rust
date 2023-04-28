/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.colors

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.util.NlsContexts.AttributeDescriptor
import org.rust.RsBundle
import java.util.function.Supplier
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

/**
 * See [RsColorSettingsPage] and [org.rust.ide.highlight.RsHighlighter]
 */
enum class RsColor(humanName: Supplier<@AttributeDescriptor String>, default: TextAttributesKey? = null) {
    VARIABLE(RsBundle.messagePointer("settings.rust.color.variables.default"), Default.IDENTIFIER),
    MUT_BINDING(RsBundle.messagePointer("settings.rust.color.mutable.binding"), Default.IDENTIFIER),
    FIELD(RsBundle.messagePointer("settings.rust.color.field"), Default.INSTANCE_FIELD),
    CONSTANT(RsBundle.messagePointer("settings.rust.color.constant"), Default.CONSTANT),
    STATIC(RsBundle.messagePointer("settings.rust.color.static"), VARIABLE.textAttributesKey),
    MUT_STATIC(RsBundle.messagePointer("settings.rust.color.static.mutable"), MUT_BINDING.textAttributesKey),

    FUNCTION(RsBundle.messagePointer("settings.rust.color.function.declaration"), Default.FUNCTION_DECLARATION),
    METHOD(RsBundle.messagePointer("settings.rust.color.method.declaration"), Default.INSTANCE_METHOD),
    ASSOC_FUNCTION(RsBundle.messagePointer("settings.rust.color.associated.function.declaration"), Default.STATIC_METHOD),
    FUNCTION_CALL(RsBundle.messagePointer("settings.rust.color.function.call"), Default.FUNCTION_CALL),
    METHOD_CALL(RsBundle.messagePointer("settings.rust.color.method.call"), Default.FUNCTION_CALL),
    ASSOC_FUNCTION_CALL(RsBundle.messagePointer("settings.rust.color.associated.function.call"), Default.STATIC_METHOD),
    MACRO(RsBundle.messagePointer("settings.rust.color.macro"), Default.IDENTIFIER),

    PARAMETER(RsBundle.messagePointer("settings.rust.color.parameter"), Default.PARAMETER),
    MUT_PARAMETER(RsBundle.messagePointer("settings.rust.color.mutable.parameter"), Default.PARAMETER),
    SELF_PARAMETER(RsBundle.messagePointer("settings.rust.color.self.parameter"), Default.KEYWORD),
    LIFETIME(RsBundle.messagePointer("settings.rust.color.lifetime"), Default.IDENTIFIER),
    TYPE_PARAMETER(RsBundle.messagePointer("settings.rust.color.type.parameter"), Default.IDENTIFIER),
    CONST_PARAMETER(RsBundle.messagePointer("settings.rust.color.const.parameter"), Default.CONSTANT),

    PRIMITIVE_TYPE(RsBundle.messagePointer("settings.rust.color.primitive"), Default.KEYWORD),
    STRUCT(RsBundle.messagePointer("settings.rust.color.struct"), Default.CLASS_NAME),
    UNION(RsBundle.messagePointer("settings.rust.color.union"), Default.CLASS_NAME),
    TRAIT(RsBundle.messagePointer("settings.rust.color.trait"), Default.INTERFACE_NAME),
    ENUM(RsBundle.messagePointer("settings.rust.color.enum"), Default.CLASS_NAME),
    ENUM_VARIANT(RsBundle.messagePointer("settings.rust.color.enum.variant"), Default.STATIC_FIELD),
    TYPE_ALIAS(RsBundle.messagePointer("settings.rust.color.type.alias"), Default.CLASS_NAME),
    CRATE(RsBundle.messagePointer("settings.rust.color.crate"), Default.IDENTIFIER),
    MODULE(RsBundle.messagePointer("settings.rust.color.module"), Default.IDENTIFIER),

    KEYWORD(RsBundle.messagePointer("settings.rust.color.keyword"), Default.KEYWORD),
    KEYWORD_UNSAFE(RsBundle.messagePointer("settings.rust.color.keyword.unsafe"), Default.KEYWORD),

    CHAR(RsBundle.messagePointer("settings.rust.color.char"), Default.STRING),
    NUMBER(RsBundle.messagePointer("settings.rust.color.number"), Default.NUMBER),
    STRING(RsBundle.messagePointer("settings.rust.color.string"), Default.STRING),
    VALID_STRING_ESCAPE(RsBundle.messagePointer("settings.rust.color.valid.escape.sequence"), Default.VALID_STRING_ESCAPE),
    INVALID_STRING_ESCAPE(RsBundle.messagePointer("settings.rust.color.invalid.escape.sequence"), Default.INVALID_STRING_ESCAPE),
    FORMAT_PARAMETER(RsBundle.messagePointer("settings.rust.color.format.parameter"), Default.VALID_STRING_ESCAPE),
    FORMAT_SPECIFIER(RsBundle.messagePointer("settings.rust.color.format.specifier"), HighlighterColors.TEXT),

    BLOCK_COMMENT(OptionsBundle.messagePointer("options.language.defaults.block.comment"), Default.BLOCK_COMMENT),
    EOL_COMMENT(OptionsBundle.messagePointer("options.language.defaults.line.comment"), Default.LINE_COMMENT),

    DOC_COMMENT(RsBundle.messagePointer("settings.rust.color.rustdoc.comment"), Default.DOC_COMMENT),
    DOC_HEADING(RsBundle.messagePointer("settings.rust.color.rustdoc.heading"), Default.DOC_COMMENT_TAG),
    DOC_LINK(RsBundle.messagePointer("settings.rust.color.rustdoc.link"), Default.DOC_COMMENT_TAG_VALUE),
    DOC_EMPHASIS(RsBundle.messagePointer("settings.rust.color.rustdoc.italic")),
    DOC_STRONG(RsBundle.messagePointer("settings.rust.color.rustdoc.bold")),
    DOC_CODE(RsBundle.messagePointer("settings.rust.color.rustdoc.code"), Default.DOC_COMMENT_MARKUP),

    BRACES(OptionsBundle.messagePointer("options.language.defaults.braces"), Default.BRACES),
    BRACKETS(OptionsBundle.messagePointer("options.language.defaults.brackets"), Default.BRACKETS),
    OPERATORS(RsBundle.messagePointer("settings.rust.color.operation.sign"), Default.OPERATION_SIGN),
    Q_OPERATOR(RsBundle.messagePointer("settings.rust.color.question.mark"), Default.KEYWORD),
    SEMICOLON(OptionsBundle.messagePointer("options.language.defaults.semicolon"), Default.SEMICOLON),
    DOT(OptionsBundle.messagePointer("options.language.defaults.dot"), Default.DOT),
    COMMA(OptionsBundle.messagePointer("options.language.defaults.comma"), Default.COMMA),
    PARENTHESES(OptionsBundle.messagePointer("options.language.defaults.parentheses"), Default.PARENTHESES),

    ATTRIBUTE(RsBundle.messagePointer("settings.rust.color.attribute"), Default.METADATA),
    UNSAFE_CODE(RsBundle.messagePointer("settings.rust.color.unsafe.code")),
    CFG_DISABLED_CODE(RsBundle.messagePointer("settings.rust.color.conditionally.disabled.code")),
    GENERATED_ITEM(RsBundle.messagePointer("settings.rust.color.generated.items")),
    ;

    val textAttributesKey = TextAttributesKey.createTextAttributesKey("org.rust.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
    val testSeverity: HighlightSeverity = HighlightSeverity(name, HighlightSeverity.INFORMATION.myVal)
}
