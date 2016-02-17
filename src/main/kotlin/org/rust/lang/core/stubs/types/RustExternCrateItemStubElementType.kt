package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustExternCrateItem
import org.rust.lang.core.psi.impl.RustExternCrateItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustExternCrateItemStubElementType(debugName: String)
    : RustItemStubElementType<RustExternCrateItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustExternCrateItem =
        RustExternCrateItemImpl(stub, this)

}
