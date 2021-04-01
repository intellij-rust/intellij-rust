/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.io.StreamUtil
import org.rust.ide.highlight.RsHighlighter
import org.rust.ide.icons.RsIcons

class RsColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Rust"
    override fun getIcon() = RsIcons.RUST
    override fun getAttributeDescriptors() = ATTRS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getHighlighter() = RsHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATOR_TAGS
    override fun getDemoText() = DEMO_TEXT

    companion object {
        private val ATTRS: Array<AttributesDescriptor> = RsColor.values().map { it.attributesDescriptor }.toTypedArray()

        // This tags should be kept in sync with RsHighlightingAnnotator highlighting logic
        private val ANNOTATOR_TAGS: Map<String, TextAttributesKey> = RsColor.values().associateBy({ it.name }, { it.textAttributesKey })

        private val DEMO_TEXT: String by lazy {
            // TODO: The annotations in this file should be generable, and would be more accurate for it.
            val stream = RsColorSettingsPage::class.java.classLoader
                .getResourceAsStream("org/rust/ide/colors/highlighterDemoText.rs")
            StreamUtil.convertSeparators(StreamUtil.readText(stream, "UTF-8"))
        }
    }
}
