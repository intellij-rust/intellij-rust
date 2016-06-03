package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustStaticItemElement
import org.rust.lang.core.psi.impl.RustStaticItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustStaticItemStubElementType(debugName: String)
    : RustItemStubElementType<RustStaticItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustStaticItemElement =
        RustStaticItemElementImpl(stub, this)

}
