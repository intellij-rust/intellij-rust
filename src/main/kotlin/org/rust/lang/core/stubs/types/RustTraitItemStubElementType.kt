package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.impl.RustTraitItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustTraitItemStubElementType(debugName: String)
    : RustItemStubElementType<RustTraitItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustTraitItemElement =
        RustTraitItemElementImpl(stub, this)
}
