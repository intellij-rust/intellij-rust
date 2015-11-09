package org.rust.lang.colorscheme

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.io.StreamUtil
import org.rust.lang.highlight.RustHighlighter
import org.rust.lang.i18n.RustBundle
import org.rust.lang.icons.RustIcons

public class RustColorSettingsPage : ColorSettingsPage {
    private fun d(displayName: String, key: TextAttributesKey) = AttributesDescriptor(displayName, key)
    private val ATTRS = arrayOf(
            d(RustBundle.message("rust.settings.colors.identifier"), RustColors.IDENTIFIER),
            d(RustBundle.message("rust.settings.colors.lifetime"), RustColors.LIFETIME),
            d(RustBundle.message("rust.settings.colors.char"), RustColors.CHAR),
            d(RustBundle.message("rust.settings.colors.string"), RustColors.STRING),
            d(RustBundle.message("rust.settings.colors.number"), RustColors.NUMBER),
            d(RustBundle.message("rust.settings.colors.keyword"), RustColors.KEYWORD),
            d(RustBundle.message("rust.settings.colors.block_comment"), RustColors.BLOCK_COMMENT),
            d(RustBundle.message("rust.settings.colors.eol_comment"), RustColors.EOL_COMMENT),
            d(RustBundle.message("rust.settings.colors.doc_comment"), RustColors.DOC_COMMENT),
            d(RustBundle.message("rust.settings.colors.parenthesis"), RustColors.PARENTHESIS),
            d(RustBundle.message("rust.settings.colors.brackets"), RustColors.BRACKETS),
            d(RustBundle.message("rust.settings.colors.braces"), RustColors.BRACES),
            d(RustBundle.message("rust.settings.colors.operators"), RustColors.OPERATORS),
            d(RustBundle.message("rust.settings.colors.semicolon"), RustColors.SEMICOLON),
            d(RustBundle.message("rust.settings.colors.dot"), RustColors.DOT),
            d(RustBundle.message("rust.settings.colors.comma"), RustColors.COMMA),
            d(RustBundle.message("rust.settings.colors.attribute"), RustColors.ATTRIBUTE),
            d(RustBundle.message("rust.settings.colors.macro"), RustColors.MACRO),
            d(RustBundle.message("rust.settings.colors.type_parameter"), RustColors.TYPE_PARAMETER)
    )
    private val TAGS = emptyMap<String, TextAttributesKey>()
    private val DEMO_TEXT by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("org/rust/lang/colorscheme/highlighterDemoText.rs")
        StreamUtil.readText(stream, "UTF-8")
    }

    override fun getDisplayName() = RustBundle.message("rust.display_name")
    override fun getIcon() = RustIcons.FILE
    override fun getAttributeDescriptors() = ATTRS
    override fun getColorDescriptors() = ColorDescriptor.EMPTY_ARRAY
    override fun getHighlighter() = RustHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = TAGS
    override fun getDemoText() = DEMO_TEXT
}
