package org.rust.lang.codestyle

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class RustCodeStyleSettings constructor(container: CodeStyleSettings) :
        CustomCodeStyleSettings(RustCodeStyleSettings::class.java.simpleName, container)
