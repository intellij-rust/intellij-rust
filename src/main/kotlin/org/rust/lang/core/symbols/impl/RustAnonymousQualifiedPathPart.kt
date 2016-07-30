package org.rust.lang.core.symbols.impl

import org.rust.lang.core.symbols.RustQualifiedPathPart

object RustAnonymousQualifiedPathPart : RustQualifiedPathPart {

    override val name: String
        get() = "<anonymous>"

    override fun equals(other: Any?): Boolean = other is RustAnonymousQualifiedPathPart

    override fun hashCode(): Int = 5189

}

