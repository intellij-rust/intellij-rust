package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.impl.RustFnItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustFnItemStubElementType(debugName: String)
    : RustItemStubElementType<RustFnItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustFnItemElement =
        RustFnItemElementImpl(stub, this)
}
