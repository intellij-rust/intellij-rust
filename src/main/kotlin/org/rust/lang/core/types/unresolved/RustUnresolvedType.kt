package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

interface RustUnresolvedType {

    fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T

}

