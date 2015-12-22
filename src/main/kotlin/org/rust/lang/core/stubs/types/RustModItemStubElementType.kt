package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustModItemStubElementType(debugName: String)
    : RustItemStubElementType<RustModItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustModItem =
        RustModItemImpl(stub, this)

}
