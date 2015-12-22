package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.stubs.RustItemStub
import org.rust.lang.core.stubs.index.RustItemIndex

abstract class RustItemStubElementType<PsiT: RustItem>(debugName: String)
    : RustStubElementType<RustItemStub, PsiT>(debugName) {

    override fun serialize(stub: RustItemStub, dataStream: StubOutputStream) =
        dataStream.writeName(stub.name)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustItemStub =
        RustItemStub(parentStub, this, dataStream.readName())

    override fun createStub(psi: PsiT, parentStub: StubElement<*>?): RustItemStub =
        RustItemStub(parentStub, this, psi.name ?: "")

    final override fun indexStub(stub: RustItemStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        val indexName = stub.name
        if (indexName != null && !indexName.isBlank()) {
            sink.occurrence(RustItemIndex.KEY, indexName)
            for (key in additionalIndexKeys) {
                sink.occurrence(key, indexName)
            }
        }
    }

    open val additionalIndexKeys: Array<StubIndexKey<String, RustItem>>
        get() = emptyArray()
}
