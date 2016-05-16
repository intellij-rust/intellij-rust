package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItem

object RustUnknownType : RustResolvedTypeBase() {
    override val inheritedImplsInner: Collection<RustImplItem> = emptyList()
    override fun toString(): String = "<unknown type>"
}
