package org.rust.lang.core.type.unresolved

import org.rust.lang.core.type.visitors.RustUnresolvedTypeVisitor

interface RustUnresolvedType {

    fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T

}

