package org.rust.lang.core.type.visitors

import org.rust.lang.core.type.unresolved.RustUnresolvedPathType

/**
 * Unresolved types visitor trait
 */
interface RustUnresolvedTypeVisitor<T> {

    fun visit(type: RustUnresolvedPathType): T

}
