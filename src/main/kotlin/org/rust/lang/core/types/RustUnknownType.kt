package org.rust.lang.core.types

import org.rust.lang.core.types.unresolved.RustUnresolvedTypeBase
import org.rust.lang.core.types.visitors.RustTypeVisitor
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

object RustUnknownType : RustUnresolvedTypeBase(), RustType {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitUnknown(this)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitUnknown(this)

    override fun toString(): String = "<unknown>"

}
