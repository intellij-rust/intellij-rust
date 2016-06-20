package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

abstract class RustNamedStubElementType<StubT, PsiT>(debugName: String) : RustStubElementType<StubT, PsiT>(debugName)
    where StubT : RustNamedElementStub<*>,
          PsiT  : RustCompositeElement {

    final override fun indexStub(stub: StubT, sink: IndexSink) {
        val name = stub.name ?: return
        sink.occurrence(RustNamedElementIndex.KEY, name)
        for (key in additionalIndexingKeys) {
            sink.occurrence(key, name)
        }
    }

    protected open val additionalIndexingKeys: Collection<StubIndexKey<String, out RustNamedElement>> = emptyList()
}
