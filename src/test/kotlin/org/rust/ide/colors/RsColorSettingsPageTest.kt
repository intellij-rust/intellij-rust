/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.colors

import com.intellij.application.options.colors.highlighting.HighlightData
import com.intellij.application.options.colors.highlighting.HighlightsExtractor
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.rust.RsTestBase
import org.rust.stdext.mapToSet

class RsColorSettingsPageTest : RsTestBase() {

    fun `test color settings demo page`() {
        val page = ColorSettingsPage.EP_NAME.findExtension(RsColorSettingsPage::class.java)
            ?: error("Cannot find ${ColorSettingsPage::class.simpleName}. Check if it's registered in plugin manifest")

        val explicitKeys = collectExplicitHighlightingKeys(page)
        val syntaxKeys = collectSyntaxHighlightingKeys(page)
        val highlightingKeys = explicitKeys + syntaxKeys

        val colors = RsColor.values().filter { it.textAttributesKey !in highlightingKeys && it !in EXCLUDED_COLORS }

        if (colors.isNotEmpty()) {
            error("""There aren't examples in color settings page for the following colors:
                ${colors.joinToString(separator = ",\n    ", prefix = "    ")}
                Please, add the example in ${RsColorSettingsPage::class.simpleName}.getDemoText""".trimIndent()
            )
        }
    }

    private fun collectExplicitHighlightingKeys(page: ColorSettingsPage): Set<TextAttributesKey> {
        val extractor = HighlightsExtractor(
            page.additionalHighlightingTagToDescriptorMap,
            page.additionalInlineElementToDescriptorMap,
            page.additionalHighlightingTagToColorKeyMap
        )

        val highlightData = mutableListOf<HighlightData>()
        extractor.extractHighlights(page.demoText, highlightData)
        return highlightData.mapToSet { it.highlightKey }
    }

    private fun collectSyntaxHighlightingKeys(page: ColorSettingsPage): Set<TextAttributesKey> {
        val syntaxHighlighter = page.highlighter
        val highlighter = HighlighterFactory.createHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().globalScheme)
        highlighter.setText(page.demoText)

        val keys = mutableSetOf<TextAttributesKey>()
        val iterator = highlighter.createIterator(0)
        while (!iterator.atEnd()) {
            val tokenType = iterator.tokenType
            keys += syntaxHighlighter.getTokenHighlights(tokenType)
            iterator.advance()
        }

        return keys
    }

    companion object {
        private val EXCLUDED_COLORS = setOf(RsColor.GENERATED_ITEM)
    }
}
