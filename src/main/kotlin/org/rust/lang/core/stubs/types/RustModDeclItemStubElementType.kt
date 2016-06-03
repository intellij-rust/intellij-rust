package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.psi.impl.RustModDeclItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustModDeclItemStubElementType(debugName: String)
    : RustItemStubElementType<RustItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustItemElement =
        RustModDeclItemElementImpl(stub, this)

}
