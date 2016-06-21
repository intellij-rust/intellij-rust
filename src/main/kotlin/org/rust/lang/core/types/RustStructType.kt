package org.rust.lang.core.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impls
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustStructType(val struct: RustStructItemElement) : RustType {


    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitStruct(this)

    override fun equals(other: Any?): Boolean = other is RustStructType && other.struct === struct

    override fun hashCode(): Int = struct.hashCode() * 10067 + 9631

    override fun toString(): String = struct.name ?: "<anonymous>"

    override val inherentImpls: Collection<RustImplItemElement> by lazy {
        struct.containingMod
            ?.impls.orEmpty()
            .filter { it.traitRef == null && (it.type?.resolvedType == this) }
    }
}
