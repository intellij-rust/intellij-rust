package org.rust.lang

import com.intellij.lang.Language

object RustLanguage : Language("Rust", "text/rust", "text/x-rust", "application/x-rust") {
    override fun isCaseSensitive() = true

    override fun getDisplayName() = "Rust"
}

