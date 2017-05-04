package org.rust.lang.core.types.types

object RustStringSliceType : RustPrimitiveType { // TODO: should not be primitive perhaps
    override fun toString(): String = "str"
}
