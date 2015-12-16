package org.rust.ide.codestyle

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import org.rust.lang.RustLanguage

class RustCodeStyleSettingsProvider : CodeStyleSettingsProvider() {

    override fun createCustomSettings(settings: CodeStyleSettings) = RustCodeStyleSettings(settings)

    override fun getConfigurableDisplayName() = "Rust"

    override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable {
        return object : CodeStyleAbstractConfigurable(settings, originalSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings) = RustCodeStyleMainPanel(currentSettings, settings)
            override fun getHelpTopic() = null
        }
    }

    private class RustCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
            TabbedLanguageCodeStylePanel(RustLanguage, currentSettings, settings) {

        override fun initTabs(settings: CodeStyleSettings?) {
            addWrappingAndBracesTab(settings)
        }
    }
}
