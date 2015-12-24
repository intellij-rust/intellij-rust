package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustStaticItem
import org.rust.lang.core.psi.impl.RustStaticItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustStaticItemStubElementType(debugName: String)
    : RustItemStubElementType<RustStaticItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustStaticItem =
        RustStaticItemImpl(stub, this)

}
