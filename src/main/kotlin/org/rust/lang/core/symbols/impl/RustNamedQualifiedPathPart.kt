package org.rust.lang.core.symbols.impl

import org.rust.lang.core.symbols.RustQualifiedPathPart

class RustNamedQualifiedPathPart(override val name: String) : RustQualifiedPathPart {

    override fun equals(other: Any?): Boolean =
        other is RustNamedQualifiedPathPart && other.name == name

    override fun hashCode(): Int = name.hashCode() * 7481

}
