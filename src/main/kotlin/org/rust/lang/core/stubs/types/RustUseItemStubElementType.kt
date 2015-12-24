package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustUseItem
import org.rust.lang.core.psi.impl.RustUseItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustUseItemStubElementType(debugName: String)
    : RustItemStubElementType<RustUseItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustUseItem =
        RustUseItemImpl(stub, this)
}
