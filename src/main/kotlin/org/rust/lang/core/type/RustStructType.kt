package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItem
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustStructItem
import org.rust.lang.core.psi.util.parentOfType

class RustStructType(val struct: RustStructItem) : RustResolvedTypeBase() {
    override val inheritedImplsInner: Collection<RustImplItem> get() {
        val potentialImpls = struct.parentOfType<RustMod>()?.items.orEmpty().filterIsInstance<RustImplItem>()
        return potentialImpls.filter { impl ->
            impl.traitRef == null && (impl.type?.resolvedType == this)
        }
    }

    override fun toString(): String = "<struct type $struct>"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RustStructType

        if (struct != other.struct) return false

        return true
    }

    override fun hashCode(): Int {
        return struct.hashCode()
    }
}
