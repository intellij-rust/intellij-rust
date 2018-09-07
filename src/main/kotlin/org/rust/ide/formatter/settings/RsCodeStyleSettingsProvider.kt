/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import org.rust.lang.RsLanguage

class RsCodeStyleSettingsProvider : CodeStyleSettingsProvider() {

    override fun createCustomSettings(settings: CodeStyleSettings): RsCodeStyleSettings =
        RsCodeStyleSettings(settings)

    override fun getConfigurableDisplayName(): String =
        RsLanguage.displayName

    override fun createSettingsPage(
        settings: CodeStyleSettings,
        originalSettings: CodeStyleSettings
    ): CodeStyleAbstractConfigurable =
        object : CodeStyleAbstractConfigurable(settings, originalSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings) = RsCodeStyleMainPanel(currentSettings, settings)
            override fun getHelpTopic(): String? = null
        }

    private class RsCodeStyleMainPanel(
        currentSettings: CodeStyleSettings,
        settings: CodeStyleSettings
    ) : TabbedLanguageCodeStylePanel(RsLanguage, currentSettings, settings) {
        override fun initTabs(settings: CodeStyleSettings?) {
            addIndentOptionsTab(settings)
            addWrappingAndBracesTab(settings)
            addBlankLinesTab(settings)
        }
    }
}
