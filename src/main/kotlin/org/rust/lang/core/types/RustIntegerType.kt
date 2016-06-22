package org.rust.lang.core.types

import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustIntegerType(val kind: Kind): RustType, RustUnresolvedType {

    companion object {
        fun from(text: String): RustIntegerType? =
            Kind.values().map { it.name to it }.toMap().let { mapped ->
                if (mapped.containsKey(text)) RustIntegerType(mapped[text]!!) else null
            }
    }
    enum class Kind { i8, i16, i32, i64 }

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitInteger(this)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitInteger(this)

    override fun equals(other: Any?): Boolean = other is RustIntegerType && other.kind === kind

    override fun hashCode(): Int = kind.hashCode()

    override fun toString(): String = kind.toString()

}
