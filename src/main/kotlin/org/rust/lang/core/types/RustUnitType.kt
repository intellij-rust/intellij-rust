package org.rust.lang.core.types

import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustUnitType : RustUnresolvedTypeBase(), RustType {

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnitType(this)

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitUnitType(this)

    override fun toString(): String = "()"
}

