package org.rust.ide.colorScheme

import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.rust.ide.highlight.syntax.RustHighlighter
import org.rust.ide.icons.RustIcons
import org.rust.ide.utils.loadCodeSampleResource

class RustColorSettingsPage : ColorSettingsPage {
    private val ATTRS = RustColor.values().map { it.attributesDescriptor }.toTypedArray()

    // This tags should be kept in sync with RustHighlightingAnnotator highlighting logic
    private val ANNOTATOR_TAGS = mapOf(
        "attribute" to RustColor.ATTRIBUTE,
        "macro" to RustColor.MACRO,
        "type-parameter" to RustColor.TYPE_PARAMETER,
        "mut-binding" to RustColor.MUT_BINDING,
        "function-decl" to RustColor.FUNCTION_DECLARATION,
        "instance-method-decl" to RustColor.INSTANCE_METHOD,
        "static-method-decl" to RustColor.STATIC_METHOD
    ).mapValues { it.value.textAttributesKey }

    private val DEMO_TEXT by lazy {
        loadCodeSampleResource("org/rust/ide/colorscheme/highlighterDemoText.rs")
    }

    override fun getDisplayName() = "Rust"
    override fun getIcon() = RustIcons.RUST
    override fun getAttributeDescriptors() = ATTRS
    override fun getColorDescriptors() = ColorDescriptor.EMPTY_ARRAY
    override fun getHighlighter() = RustHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATOR_TAGS
    override fun getDemoText() = DEMO_TEXT
}
