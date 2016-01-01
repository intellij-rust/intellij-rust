package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustEnumItem
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.impl.RustEnumItemImpl
import org.rust.lang.core.stubs.RustItemStub
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustEnumItemStubElementType(debugName: String)
    : RustItemStubElementType<RustEnumItem>(debugName) {

    override fun createPsi(stub: RustItemStub): RustEnumItem =
        RustEnumItemImpl(stub, this)

    override val additionalIndexKeys: Array<StubIndexKey<String, RustItem>>
        get() = arrayOf(RustStructOrEnumIndex.KEY)
}
