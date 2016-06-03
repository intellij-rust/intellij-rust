package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.impl.RustModItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustModItemStubElementType(debugName: String)
    : RustItemStubElementType<RustModItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustModItemElement =
        RustModItemElementImpl(stub, this)

}
