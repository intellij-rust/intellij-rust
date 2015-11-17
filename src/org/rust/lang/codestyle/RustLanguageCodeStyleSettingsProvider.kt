package org.rust.lang.codestyle

import com.intellij.openapi.util.io.StreamUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.rust.lang.RustLanguage

class RustLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage() = RustLanguage

    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable,
                                   settingsType: LanguageCodeStyleSettingsProvider.SettingsType) {

        if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
            consumer.showStandardOptions("RIGHT_MARGIN");
        }
    }

    override fun getDefaultCommonSettings() = CommonCodeStyleSettings(language).apply {
        RIGHT_MARGIN = 99
    }

    override fun getCodeSample(settingsType: LanguageCodeStyleSettingsProvider.SettingsType) = CODE_SAMPLE

    private val CODE_SAMPLE by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("org/rust/lang/codestyle/code_sample.rs")
        StreamUtil.readText(stream, "UTF-8")
    }
}
