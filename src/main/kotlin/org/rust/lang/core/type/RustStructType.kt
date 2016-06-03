package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.util.parentOfType

data class RustStructType(val struct: RustStructItemElement) : RustResolvedType {
    override val inheritedImpls: Collection<RustImplItemElement> by lazy {
        struct.parentOfType<RustMod>()?.itemList.orEmpty()
            .filterIsInstance<RustImplItemElement>()
            .filter { it.traitRef == null && (it.type?.resolvedType == this) }
    }

    override fun toString(): String = "<struct type $struct>"

}
