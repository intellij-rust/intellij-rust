package org.rust.lang.core.types

import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustTupleType(private val elements: List<RustType>) : RustTypeBase() {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitTupleType(this)

//    override fun equals(other: Any?): Boolean = other is RustTupleType && other.elements == elements
//
//    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = elements.joinToString(", ", "(", ")")

    operator fun get(i: Int): RustType {
        require(i >= 0)
        return elements.getOrElse(i, { RustUnknownType })
    }

    val types: Iterable<RustType>
        get() = elements

    val size: Int = elements.size
}

