package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustForeignFnItem
import org.rust.lang.core.psi.impl.RustForeignFnItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustForeignFnItemStubElementType(debugName: String)
    : RustItemStubElementType<RustForeignFnItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustForeignFnItem =
        RustForeignFnItemImpl(stub, this)

}
