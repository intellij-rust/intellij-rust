package org.rust.lang.core.types

import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustStringType : RustType, RustUnresolvedType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitString(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitString(this)

    override fun equals(other: Any?): Boolean = other is RustStringType

    override fun hashCode(): Int = 10709

    override fun toString(): String = "str"
}
