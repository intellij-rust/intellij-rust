package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IndexSink
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

abstract class RustNamedStubElementType<StubT, PsiT>(debugName: String) : RustStubElementType<StubT, PsiT>(debugName)
    where StubT : RustNamedElementStub<PsiT>,
          PsiT  : RustNamedElement {

    override fun indexStub(stub: StubT, sink: IndexSink) {
        stub.name?.let { sink.occurrence(RustNamedElementIndex.KEY, it) }
    }

}
