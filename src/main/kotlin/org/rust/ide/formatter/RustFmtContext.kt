package org.rust.ide.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.rust.ide.formatter.impl.createSpacingBuilder
import org.rust.ide.formatter.settings.RustCodeStyleSettings
import org.rust.lang.RustLanguage

data class RustFmtContext private constructor(
    val commonSettings: CommonCodeStyleSettings,
    val rustSettings: RustCodeStyleSettings,
    val spacingBuilder: SpacingBuilder
) {
    companion object {
        fun create(settings: CodeStyleSettings): RustFmtContext {
            val commonSettings = settings.getCommonSettings(RustLanguage)
            val rustSettings = settings.getCustomSettings(RustCodeStyleSettings::class.java)
            return RustFmtContext(commonSettings, rustSettings, createSpacingBuilder(commonSettings, rustSettings))
        }
    }
}
