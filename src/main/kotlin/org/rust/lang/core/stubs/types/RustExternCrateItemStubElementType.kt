package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.psi.impl.RustExternCrateItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

class RustExternCrateItemStubElementType(debugName: String)
    : RustItemStubElementType<RustExternCrateItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustExternCrateItemElement =
        RustExternCrateItemElementImpl(stub, this)

}
