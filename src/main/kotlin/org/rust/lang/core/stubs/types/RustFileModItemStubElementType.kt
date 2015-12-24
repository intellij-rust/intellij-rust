package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.impl.RustFileModItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustFileModItemStubElementType(debugName: String)
    : RustItemStubElementType<RustFileModItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustFileModItem =
        RustFileModItemImpl(stub, this)
}
