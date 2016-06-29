package org.rust.lang.core.types.util

import org.rust.lang.core.types.RustReferenceType
import org.rust.lang.core.types.RustType

/**
 * Util to get through reference-types if any present
 */
fun RustType.stripAllRefsIfAny(): RustType =
    when (this) {
        is RustReferenceType -> referenced.stripAllRefsIfAny()
        else -> this
    }
