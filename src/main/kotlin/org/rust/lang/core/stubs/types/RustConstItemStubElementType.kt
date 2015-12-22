package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustConstItem
import org.rust.lang.core.psi.impl.RustConstItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustConstItemStubElementType(debugName: String)
    : RustItemStubElementType<RustConstItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustConstItem =
        RustConstItemImpl(stub, this)

}
