package org.rust.lang

import com.intellij.lang.Language

public open class RustLanguage : Language("RUST") {

    // XXX: can't use companion object here because companions are loaded lazily
    object INSTANCE : RustLanguage() {}

    override fun isCaseSensitive() = true
}

