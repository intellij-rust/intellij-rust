package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IndexSink
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

abstract class RustNamedStubElementType<StubT, PsiT>(debugName: String) : RustStubElementType<StubT, PsiT>(debugName)
    where StubT : RustNamedElementStub<*>,
          PsiT  : RustCompositeElement {

    final override fun indexStub(stub: StubT, sink: IndexSink) {
        stub.name?.let { sink.occurrence(RustNamedElementIndex.KEY, it) }
        additionalIndexing(stub, sink)
    }

    protected open fun additionalIndexing(stub: StubT, sink: IndexSink) {}
}
