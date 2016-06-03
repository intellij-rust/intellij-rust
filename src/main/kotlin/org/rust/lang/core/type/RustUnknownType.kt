package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItemElement

object RustUnknownType : RustResolvedType {
    override val inheritedImpls: Collection<RustImplItemElement> = emptyList()
    override fun toString(): String = "<unknown type>"
}
