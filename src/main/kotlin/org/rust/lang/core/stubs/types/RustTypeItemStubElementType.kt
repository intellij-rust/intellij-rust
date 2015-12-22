package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustTypeItem
import org.rust.lang.core.psi.impl.RustTypeItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustTypeItemStubElementType(debugName: String)
    : RustItemStubElementType<RustTypeItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustTypeItem =
        RustTypeItemImpl(stub, this)
}
