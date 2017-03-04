package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType

data class RustPointerType(val referenced: RustType, val mutable: Boolean = false) : RustType {
    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"
}
