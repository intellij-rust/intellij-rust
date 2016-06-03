package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.stubs.RustItemStub
import org.rust.lang.core.stubs.index.RustNamedElementIndex

abstract class RustItemStubElementType<PsiT: RustItemElement>(debugName: String)
    : RustStubElementType<RustItemStub, PsiT>(debugName) {

    override fun serialize(stub: RustItemStub, dataStream: StubOutputStream) =
        dataStream.writeName(stub.name)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustItemStub =
        RustItemStub(parentStub, this, dataStream.readName()!!)

    override fun createStub(psi: PsiT, parentStub: StubElement<*>?): RustItemStub =
        RustItemStub(parentStub, this, psi.name ?: "")

    final override fun indexStub(stub: RustItemStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        val indexName = stub.name
        if (indexName != null && !indexName.isBlank()) {
            sink.occurrence(RustNamedElementIndex.KEY, indexName)
            for (key in additionalIndexKeys) {
                sink.occurrence(key, indexName)
            }
        }
    }

    open protected val additionalIndexKeys: Array<StubIndexKey<String, RustStructOrEnum>>
        get() = emptyArray()
}
