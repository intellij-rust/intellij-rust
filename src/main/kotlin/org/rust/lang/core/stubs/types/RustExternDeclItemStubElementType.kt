package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustExternDeclItem
import org.rust.lang.core.psi.impl.RustExternDeclItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustExternDeclItemStubElementType(debugName: String)
    : RustItemStubElementType<RustExternDeclItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustExternDeclItem =
        RustExternDeclItemImpl(stub, this)

}
