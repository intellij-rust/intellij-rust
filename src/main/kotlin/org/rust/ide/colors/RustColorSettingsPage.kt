package org.rust.ide.colors

import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.rust.ide.highlight.syntax.RustHighlighter
import org.rust.ide.icons.RustIcons
import org.rust.ide.utils.loadCodeSampleResource

class RustColorSettingsPage : ColorSettingsPage {
    private val ATTRS = RustColor.values().map { it.attributesDescriptor }.toTypedArray()

    // This tags should be kept in sync with RustHighlightingAnnotator highlighting logic
    // @TODO Figure out how to namespace menu elements a la
    //       https://github.com/JetBrains/kotlin/blob/8b30e7ef4e48494ba245b441a5b23142f1d6ae33/idea/idea-analysis/src/org/jetbrains/kotlin/idea/KotlinBundle.properties
    private val ANNOTATOR_TAGS = RustColor.values().associateBy({ it.name }, {it.textAttributesKey })


    private val DEMO_TEXT by lazy {
        // @TODO The annotations in this file should be generable, and would be more accurate for it.
        loadCodeSampleResource("org/rust/ide/colors/highlighterDemoText.rs")
    }

    override fun getDisplayName() = "Rust"
    override fun getIcon() = RustIcons.RUST
    override fun getAttributeDescriptors() = ATTRS
    override fun getColorDescriptors() = ColorDescriptor.EMPTY_ARRAY
    override fun getHighlighter() = RustHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATOR_TAGS
    override fun getDemoText() = DEMO_TEXT
}
