package org.rust.ide.formatter.settings

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.*
import org.rust.ide.utils.loadCodeSampleResource
import org.rust.lang.RustLanguage

class RustLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = RustLanguage

    override fun getCodeSample(settingsType: SettingsType): String =
        when (settingsType) {
            INDENT_SETTINGS -> INDENT_SAMPLE
            WRAPPING_AND_BRACES_SETTINGS -> WRAP_SAMPLE
            BLANK_LINES_SETTINGS -> BLANK_LINES_SAMPLE
            else -> ""
        }

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (settingsType) {
            BLANK_LINES_SETTINGS -> {
                consumer.showStandardOptions(
                    "KEEP_LINE_BREAKS",
                    "KEEP_BLANK_LINES_IN_DECLARATIONS",
                    "KEEP_BLANK_LINES_IN_CODE")

                consumer.showCustomOption(RustCodeStyleSettings::class.java,
                    "MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS",
                    "Between declarations:",
                    CodeStyleSettingsCustomizable.BLANK_LINES)
            }

            WRAPPING_AND_BRACES_SETTINGS -> {
                consumer.showStandardOptions(
                    "RIGHT_MARGIN",
                    "ALIGN_MULTILINE_CHAINED_METHODS",
                    "ALIGN_MULTILINE_PARAMETERS",
                    "ALIGN_MULTILINE_PARAMETERS_IN_CALLS")

                consumer.showCustomOption(RustCodeStyleSettings::class.java,
                    "ALLOW_ONE_LINE_MATCH",
                    "Match expressions in one line",
                    CodeStyleSettingsCustomizable.WRAPPING_KEEP)

                consumer.showCustomOption(RustCodeStyleSettings::class.java,
                    "ALIGN_RET_TYPE_AND_WHERE_CLAUSE",
                    "Align return type and where clause",
                    CodeStyleSettingsCustomizable.WRAPPING_METHOD_PARAMETERS)

                consumer.showCustomOption(RustCodeStyleSettings::class.java,
                    "ALIGN_TYPE_PARAMS",
                    ApplicationBundle.message("wrapping.align.when.multiline"),
                    "Type parameters")

                consumer.showCustomOption(RustCodeStyleSettings::class.java,
                    "ALIGN_WHERE_BOUNDS",
                    "Align where clause bounds",
                    "Type parameters")

                consumer.showCustomOption(RustCodeStyleSettings::class.java,
                    "INLINE_MACRO_USE_ATTR",
                    "Place #[macro_use] in-line",
                    "Attributes")
            }
        }
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor()

    override fun getDefaultCommonSettings(): CommonCodeStyleSettings =
        CommonCodeStyleSettings(language).apply {
            RIGHT_MARGIN = 100
            ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
            initIndentOptions().apply {
                // FIXME(jajakobyly): It's a hack
                // Nobody else does this and still somehow achieve similar effect
                CONTINUATION_INDENT_SIZE = INDENT_SIZE
            }
        }

    private val INDENT_SAMPLE: String by lazy {
        loadCodeSampleResource("org/rust/ide/formatter/settings/indent_sample.rs")
    }

    private val WRAP_SAMPLE: String by lazy {
        loadCodeSampleResource("org/rust/ide/formatter/settings/wrap_sample.rs")
    }

    private val BLANK_LINES_SAMPLE: String by lazy {
        loadCodeSampleResource("org/rust/ide/formatter/settings/blank_lines_sample.rs")
    }
}
