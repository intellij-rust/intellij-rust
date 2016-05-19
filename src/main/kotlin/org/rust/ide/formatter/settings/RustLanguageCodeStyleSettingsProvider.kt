package org.rust.ide.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.*
import org.rust.ide.utils.loadCodeSampleResource
import org.rust.lang.RustLanguage

class RustLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = RustLanguage

    override fun getCodeSample(settingsType: SettingsType): String =
        // TODO Provide more suitable code samples for specific settings types
        when (settingsType) {
            BLANK_LINES_SETTINGS,
            SPACING_SETTINGS,
            WRAPPING_AND_BRACES_SETTINGS,
            INDENT_SETTINGS -> CODE_SAMPLE

            else            -> ""
        }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            BLANK_LINES_SETTINGS         -> {
                consumer.showStandardOptions(
                    "KEEP_LINE_BREAKS",
                    "KEEP_BLANK_LINES_IN_DECLARATIONS",
                    "KEEP_BLANK_LINES_IN_CODE")
            }

            SPACING_SETTINGS             -> {
                // TODO Implement this.
            }

            WRAPPING_AND_BRACES_SETTINGS -> {
                consumer.showStandardOptions("RIGHT_MARGIN");
            }

            INDENT_SETTINGS              -> {
                // TODO Implement this.
            }

            else                         -> {
            }
        }
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor()

    override fun getDefaultCommonSettings(): CommonCodeStyleSettings =
        CommonCodeStyleSettings(language).apply {
            RIGHT_MARGIN = 99
        }

    private val CODE_SAMPLE: String by lazy {
        loadCodeSampleResource("org/rust/ide/formatter/settings/code_sample.rs")
    }
}
