package org.toml.lang

import com.intellij.lang.Language


public class TomlLanguage : Language("TOML", "text/toml") {

    companion object {
        public val INSTANCE: TomlLanguage = TomlLanguage();
    }
}
