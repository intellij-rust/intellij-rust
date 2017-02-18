package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType

data class RustSliceType(val elementType: RustType) : RustPrimitiveType {
    override fun toString() = "[$elementType]"
}
