package org.rust.lang.core.types.unresolved

import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.visitors.RustUnresolvedTypeVisitor


class RustUnresolvedPathType(val path: RustPath) : RustUnresolvedTypeBase() {

    override fun <T> accept(visitor: RustUnresolvedTypeVisitor<T>): T = visitor.visitPathType(this)

    override fun toString(): String = "[U] $path"
}

