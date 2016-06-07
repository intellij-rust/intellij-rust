package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.psi.impl.RustStructItemElementImpl
import org.rust.lang.core.stubs.RustItemStub
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustStructItemStubElementType(debugName: String)
    : RustItemStubElementType<RustStructItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustStructItemElement =
        RustStructItemElementImpl(stub, this)

    override val additionalIndexKeys: Array<StubIndexKey<String, RustStructOrEnum>>
        get() = arrayOf(RustStructOrEnumIndex.KEY)
}

