package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustConstItemElement
import org.rust.lang.core.psi.impl.RustConstItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustConstItemStubElementType(debugName: String)
    : RustItemStubElementType<RustConstItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustConstItemElement =
        RustConstItemElementImpl(stub, this)

}
