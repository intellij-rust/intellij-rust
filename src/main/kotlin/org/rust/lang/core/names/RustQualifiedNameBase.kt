package org.rust.lang.core.names

import org.rust.lang.core.names.parts.RustNamePart

open class RustQualifiedNameBase<T : RustNamePart>(override val part: T, override val qualifier: RustQualifiedName? = null)
    : RustQualifiedName(part, qualifier)
