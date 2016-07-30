package org.rust.lang.core.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustStructOrEnumItemElement
import org.rust.lang.core.resolve.indexes.RustImplIndex

abstract class RustStructOrEnumTypeBase(struct: RustStructOrEnumItemElement) : RustTypeBase() {

    override val impls: Sequence<RustImplItemElement> by lazy {
        RustImplIndex.findImplsFor(struct).asSequence().filter { it.traitRef == null }
    }

}
