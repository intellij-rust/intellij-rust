package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType


class RustArrayType(val base: RustType, val size: Int) : RustPrimitiveType {
    override fun toString() = "[$base; $size]"

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType =
        RustArrayType(base.substitute(map), size)
}
