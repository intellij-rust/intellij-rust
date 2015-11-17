package org.toml.lang

import com.intellij.lang.Language


open public class TomlLanguage : Language("TOML", "text/toml") {
    // XXX: can't use companion object here because companions are loaded lazily
    public object INSTANCE : TomlLanguage() {}

    override fun isCaseSensitive() = true
}
