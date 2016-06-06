package org.rust.lang.core.type

import org.rust.lang.core.type.unresolved.RustUnresolvedType
import org.rust.lang.core.type.visitors.RustTypeVisitor
import org.rust.lang.core.type.visitors.RustUnresolvedTypeVisitor

object RustUnitType : RustType, RustUnresolvedType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnitType(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitUnitType(this)

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = 9049
}

