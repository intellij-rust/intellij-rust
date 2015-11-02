package org.rust.lang

import com.intellij.lang.Language

public open class RustLanguage : Language("RUST") {

    object INSTANCE : RustLanguage() {}

    override fun isCaseSensitive() = true
}

