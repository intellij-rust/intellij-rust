package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.impl.RustModDeclItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustModDeclItemStubElementType(debugName: String)
    : RustItemStubElementType<RustItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustItem =
        RustModDeclItemImpl(stub, this)

}
