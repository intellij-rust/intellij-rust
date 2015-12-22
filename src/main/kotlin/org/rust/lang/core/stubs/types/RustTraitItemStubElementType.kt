package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustTraitItem
import org.rust.lang.core.psi.impl.RustTraitItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustTraitItemStubElementType(debugName: String)
    : RustItemStubElementType<RustTraitItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustTraitItem =
        RustTraitItemImpl(stub, this)
}
