package org.rust.ide.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.rust.lang.RustLanguage

data class RustBlockContext private constructor(
    val commonSettings: CommonCodeStyleSettings,
    val rustSettings: RustCodeStyleSettings,
    val spacingBuilder: SpacingBuilder
) {
    companion object {
        fun create(settings: CodeStyleSettings): RustBlockContext {
            val commonSettings = settings.getCommonSettings(RustLanguage)
            val rustSettings = settings.getCustomSettings(RustCodeStyleSettings::class.java)
            return RustBlockContext(commonSettings, rustSettings, createSpacingBuilder(commonSettings, rustSettings))
        }
    }
}
