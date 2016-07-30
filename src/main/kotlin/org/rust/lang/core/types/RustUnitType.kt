package org.rust.lang.core.types

import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustUnitType : RustUnresolvedTypeBase(), RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnitType(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitUnitType(this)

//    override fun equals(other: Any?): Boolean = this === other
//
//    override fun hashCode(): Int = 9049

    override fun toString(): String = "()"
}

