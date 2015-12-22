package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustStructItem
import org.rust.lang.core.psi.impl.RustStructItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustStructItemStubElementType(debugName: String)
    : RustItemStubElementType<RustStructItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustStructItem =
        RustStructItemImpl(stub, this)
}

