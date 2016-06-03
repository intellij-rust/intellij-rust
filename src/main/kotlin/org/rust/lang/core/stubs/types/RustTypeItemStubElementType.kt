package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustTypeItemElement
import org.rust.lang.core.psi.impl.RustTypeItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustTypeItemStubElementType(debugName: String)
    : RustItemStubElementType<RustTypeItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustTypeItemElement =
        RustTypeItemElementImpl(stub, this)
}
