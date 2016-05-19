package org.rust.ide.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.rust.ide.formatter.impl.createSpacingBuilder
import org.rust.ide.formatter.settings.RustCodeStyleSettings
import org.rust.lang.RustLanguage

data class RustFmtBlockContext private constructor(
    val commonSettings: CommonCodeStyleSettings,
    val rustSettings: RustCodeStyleSettings,
    val spacingBuilder: SpacingBuilder
) {
    companion object {
        fun create(settings: CodeStyleSettings): RustFmtBlockContext {
            val commonSettings = settings.getCommonSettings(RustLanguage)
            val rustSettings = settings.getCustomSettings(RustCodeStyleSettings::class.java)
            return RustFmtBlockContext(commonSettings, rustSettings, createSpacingBuilder(commonSettings, rustSettings))
        }
    }
}
