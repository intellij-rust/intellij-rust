package org.rust.lang.core.type

import org.rust.lang.core.psi.RustImplItem

abstract class RustResolvedTypeBase : RustResolvedType {
    final override val inheritedImpls: Collection<RustImplItem> by lazy { inheritedImplsInner }

    abstract protected val inheritedImplsInner: Collection<RustImplItem>
}

