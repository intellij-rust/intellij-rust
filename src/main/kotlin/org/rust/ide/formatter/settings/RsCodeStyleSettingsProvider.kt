package org.rust.ide.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import org.rust.lang.RustLanguage

class RsCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings) = RsCodeStyleSettings(settings)

    override fun getConfigurableDisplayName() = RustLanguage.displayName

    override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings) =
        object : CodeStyleAbstractConfigurable(settings, originalSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings) = RustCodeStyleMainPanel(currentSettings, settings)
            override fun getHelpTopic() = null
        }

    private class RustCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
        TabbedLanguageCodeStylePanel(RustLanguage, currentSettings, settings) {

        override fun initTabs(settings: CodeStyleSettings?) {
            addIndentOptionsTab(settings)
            addWrappingAndBracesTab(settings)
            addBlankLinesTab(settings)
        }
    }
}
