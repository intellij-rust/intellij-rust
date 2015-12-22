package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustFnItem
import org.rust.lang.core.psi.impl.RustFnItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustFnItemStubElementType(debugName: String)
    : RustItemStubElementType<RustFnItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustFnItem =
        RustFnItemImpl(stub, this)
}
