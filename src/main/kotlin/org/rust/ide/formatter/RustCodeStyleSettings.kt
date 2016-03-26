package org.rust.ide.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class RustCodeStyleSettings(container: CodeStyleSettings) :
        CustomCodeStyleSettings(RustCodeStyleSettings::class.java.simpleName, container)
