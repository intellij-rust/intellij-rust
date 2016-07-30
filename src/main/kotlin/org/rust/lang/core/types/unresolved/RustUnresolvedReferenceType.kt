package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

class RustUnresolvedReferenceType(val referenced: RustUnresolvedType, val mutable: Boolean) : RustUnresolvedTypeBase() {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitReference(this)

}
