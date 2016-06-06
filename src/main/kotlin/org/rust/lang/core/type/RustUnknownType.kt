package org.rust.lang.core.type

import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.visitors.RustTypeVisitor
import org.rust.lang.core.type.visitors.RustUnresolvedTypeVisitor

object RustUnknownType : RustType, RustUnresolvedType {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitUnknown(this)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnknown(this)

    override fun toString(): String = "<unknown type>"

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = 10499
}
