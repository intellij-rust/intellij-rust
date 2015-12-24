package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustImplItem
import org.rust.lang.core.psi.impl.RustImplItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustImplItemStubElementType(debugName: String)
    : RustItemStubElementType<RustImplItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustImplItem =
        RustImplItemImpl(stub, this)

}
