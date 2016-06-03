package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.psi.impl.RustEnumItemElementImpl
import org.rust.lang.core.stubs.RustItemStub
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustEnumItemStubElementType(debugName: String)
    : RustItemStubElementType<RustEnumItemElement>(debugName) {

    override fun createPsi(stub: RustItemStub): RustEnumItemElement =
        RustEnumItemElementImpl(stub, this)

    override val additionalIndexKeys: Array<StubIndexKey<String, RustStructOrEnum>>
        get() = arrayOf(RustStructOrEnumIndex.KEY)
}
