package org.rust.lang.core.types.types

import org.rust.lang.core.types.RustType

data class RustTupleType(val types: List<RustType>) : RustType {

    operator fun get(i: Int): RustType {
        require(i >= 0)
        return types.getOrElse(i, { RustUnknownType })
    }

    val size: Int = types.size

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustTupleType =
        RustTupleType(types.map { it.substitute(map) })

    override fun toString(): String = types.joinToString(", ", "(", ")")

}

