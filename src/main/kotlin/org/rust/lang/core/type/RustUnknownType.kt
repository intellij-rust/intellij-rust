package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItem

object RustUnknownType : RustResolvedType {
    override val inheritedImpls: Collection<RustImplItem> = emptyList()
    override fun toString(): String = "<unknown type>"
}
