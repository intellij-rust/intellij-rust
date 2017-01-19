package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType

data class RustTupleType(val types: List<RustType>) : RustType {
    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustTupleType =
        RustTupleType(types.map { it.substitute(map) })

    override fun toString(): String = types.joinToString(", ", "(", ")")
}

