package org.rust.lang

import com.intellij.lang.Language

object RustLanguage : Language("RUST") {
    override fun isCaseSensitive() = true
}

