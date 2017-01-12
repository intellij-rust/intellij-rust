package org.rust.lang.core.types

data class RustTupleType(private val elements: List<RustType>) : RustType {

    operator fun get(i: Int): RustType {
        require(i >= 0)
        return elements.getOrElse(i, { RustUnknownType })
    }

    val types: Iterable<RustType>
        get() = elements

    val size: Int = elements.size

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustTupleType =
        RustTupleType(elements.map { it.substitute(map) })

    override fun toString(): String = elements.joinToString(", ", "(", ")")

}

