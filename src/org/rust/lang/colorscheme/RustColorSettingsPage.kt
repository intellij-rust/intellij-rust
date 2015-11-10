package org.rust.lang.colorscheme

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.io.StreamUtil
import org.rust.lang.highlight.RustHighlighter
import org.rust.lang.icons.RustIcons

public class RustColorSettingsPage : ColorSettingsPage {
    private fun d(displayName: String, key: TextAttributesKey) = AttributesDescriptor(displayName, key)
    private val ATTRS = arrayOf(
            d("Identifier", RustColors.IDENTIFIER),
            d("Lifetime", RustColors.LIFETIME),
            d("Char", RustColors.CHAR),
            d("String", RustColors.STRING),
            d("Number", RustColors.NUMBER),
            d("Keyword", RustColors.KEYWORD),
            d("Block comment", RustColors.BLOCK_COMMENT),
            d("Line comment", RustColors.EOL_COMMENT),
            d("Doc comment", RustColors.DOC_COMMENT),
            d("Parenthesis", RustColors.PARENTHESIS),
            d("Brackets", RustColors.BRACKETS),
            d("Braces", RustColors.BRACES),
            d("Operator sign", RustColors.OPERATORS),
            d("Semicolon", RustColors.SEMICOLON),
            d("Dot", RustColors.DOT),
            d("Comma", RustColors.COMMA),
            d("Attribute", RustColors.ATTRIBUTE),
            d("Macro", RustColors.MACRO),
            d("Type Parameter", RustColors.TYPE_PARAMETER),
            d("Mutable binding", RustColors.MUT_BINDING)
    )
    private val TAGS = emptyMap<String, TextAttributesKey>()
    private val DEMO_TEXT by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("org/rust/lang/colorscheme/highlighterDemoText.rs")
        StreamUtil.readText(stream, "UTF-8")
    }

    override fun getDisplayName() = "Rust"
    override fun getIcon() = RustIcons.FILE
    override fun getAttributeDescriptors() = ATTRS
    override fun getColorDescriptors() = ColorDescriptor.EMPTY_ARRAY
    override fun getHighlighter() = RustHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = TAGS
    override fun getDemoText() = DEMO_TEXT
}
