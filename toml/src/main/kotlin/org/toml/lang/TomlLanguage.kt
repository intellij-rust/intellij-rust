package org.toml.lang

import com.intellij.lang.Language

object TomlLanguage : Language("TOML", "text/toml") {
    override fun isCaseSensitive() = true
}
