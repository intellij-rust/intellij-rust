package org.rust.lang.core.types.unresolved

import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor

interface RustUnresolvedType {

    fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T

    /**
     * If this is a nominal type (struct, enum or trait object), returns type's name.
     * Does **not** strip references.
     *
     * See `RustType#baseTypeName`
     */
    val nominalTypeName: String? get() = null
}

