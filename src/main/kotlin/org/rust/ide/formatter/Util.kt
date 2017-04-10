package org.rust.ide.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import org.rust.ide.formatter.settings.RsCodeStyleSettings

val CodeStyleSettings.rust: RsCodeStyleSettings
    get() = getCustomSettings(RsCodeStyleSettings::class.java)
