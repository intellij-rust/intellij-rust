package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.impl.RustImplItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustImplItemStubElementType(debugName: String)
    : RustItemStubElementType<RustImplItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustImplItemElement =
        RustImplItemElementImpl(stub, this)

}
