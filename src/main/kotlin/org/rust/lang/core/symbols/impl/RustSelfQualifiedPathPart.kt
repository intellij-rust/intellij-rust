package org.rust.lang.core.symbols.impl

import org.rust.lang.core.symbols.RustQualifiedPathPart

object RustSelfQualifiedPathPart : RustQualifiedPathPart {

    override val name: String
        get() = "self"

    override fun equals(other: Any?): Boolean = other is RustSelfQualifiedPathPart

    override fun hashCode(): Int = 5309

}
