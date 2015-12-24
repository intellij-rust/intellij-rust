package org.rust.lang.core.stubs.types

import org.rust.lang.core.psi.RustConstItem
import org.rust.lang.core.psi.RustEnumItem
import org.rust.lang.core.psi.impl.RustEnumItemImpl
import org.rust.lang.core.stubs.RustItemStub

class RustEnumItemStubElementType(debugName: String)
    : RustItemStubElementType<RustEnumItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustEnumItem =
        RustEnumItemImpl(stub, this)

}
