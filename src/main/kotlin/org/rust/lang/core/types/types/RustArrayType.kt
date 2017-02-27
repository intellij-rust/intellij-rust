package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType


class RustArrayType(val base: RustType, val size: Int) : RustPrimitiveType {
    override fun toString() = "[$base; $size]"
}
