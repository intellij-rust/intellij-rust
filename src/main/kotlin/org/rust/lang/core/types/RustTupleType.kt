package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTupleType(private val elements: List<RustType>) : RustTypeBase() {

    operator fun get(i: Int): RustType {
        require(i >= 0)
        return elements.getOrElse(i, { RustUnknownType })
    }

    val types: Iterable<RustType>
        get() = elements

    val size: Int = elements.size

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustTupleType =
        RustTupleType(elements.map { it.substitute(map) })

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTupleType(this)

    override fun toString(): String = elements.joinToString(", ", "(", ")")

}

