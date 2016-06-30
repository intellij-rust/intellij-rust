package org.rust.lang.core.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustStructOrEnumItemElement
import org.rust.lang.core.stubs.index.RustInherentImplIndex

abstract class RustStructOrEnumTypeBase(struct: RustStructOrEnumItemElement) : RustType {

    override val inherentImpls: Sequence<RustImplItemElement> by lazy {
        RustInherentImplIndex.getInherentImpls(struct.project, this).asSequence()
    }

    override val baseTypeName: String? = struct.name

}
