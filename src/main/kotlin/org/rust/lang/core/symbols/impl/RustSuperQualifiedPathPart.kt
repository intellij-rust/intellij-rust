package org.rust.lang.core.symbols.impl

import org.rust.lang.core.symbols.RustQualifiedPathPart

object RustSuperQualifiedPathPart : RustQualifiedPathPart {

    override val name: String
        get() = "super"

    override fun equals(other: Any?): Boolean = other is RustSuperQualifiedPathPart

    override fun hashCode(): Int = 5189

}
