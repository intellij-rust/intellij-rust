package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustForeignModItemElement
import org.rust.lang.core.psi.impl.RustForeignModItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustForeignModItemStubElementType(debugName: String)
    : RustItemStubElementType<RustForeignModItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustForeignModItemElement =
        RustForeignModItemElementImpl(stub, this)

}
