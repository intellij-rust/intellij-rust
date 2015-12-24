package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustForeignModItem
import org.rust.lang.core.psi.impl.RustForeignModItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustForeignModItemStubElementType(debugName: String)
    : RustItemStubElementType<RustForeignModItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustForeignModItem =
        RustForeignModItemImpl(stub, this)

}
