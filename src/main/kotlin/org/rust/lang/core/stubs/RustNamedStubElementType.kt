package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

abstract class RustNamedStubElementType<StubT, PsiT> : IStubElementType<StubT, PsiT>
    where StubT : RustNamedElementStub<*>,
          PsiT  : RustCompositeElement {

    protected constructor(debugName: String) : super(debugName, RustLanguage)

    final override fun indexStub(stub: StubT, sink: IndexSink) {
        val name = stub.name ?: return
        sink.occurrence(RustNamedElementIndex.KEY, name)
        for (key in additionalIndexingKeys) {
            sink.occurrence(key, name)
        }
    }

    final override fun getExternalId(): String = "rust.${super.toString()}"

    protected open val additionalIndexingKeys: Collection<StubIndexKey<String, out RustNamedElement>> = emptyList()
}
