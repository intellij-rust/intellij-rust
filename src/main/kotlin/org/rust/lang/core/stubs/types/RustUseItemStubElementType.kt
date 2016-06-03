package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustUseItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustUseItemStubElementType(debugName: String)
    : RustItemStubElementType<RustUseItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustUseItemElement =
        RustUseItemElementImpl(stub, this)
}
