package org.rust.lang.core.names

import org.rust.lang.core.names.parts.RustNamePart

abstract class RustQualifiedNameBase<T : RustNamePart>(override val part: T, override val qualifier: RustQualifiedName? = null)
    : RustQualifiedName(part, qualifier) {

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int
}
