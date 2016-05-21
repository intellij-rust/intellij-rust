package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.RustStructItem
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.psi.impl.RustStructItemImpl
import org.rust.lang.core.stubs.RustItemStub
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustStructItemStubElementType(debugName: String)
    : RustItemStubElementType<RustStructItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustStructItem =
        RustStructItemImpl(stub, this)

    override val additionalIndexKeys: Array<StubIndexKey<String, RustStructOrEnum>>
        get() = arrayOf(RustStructOrEnumIndex.KEY)
}

