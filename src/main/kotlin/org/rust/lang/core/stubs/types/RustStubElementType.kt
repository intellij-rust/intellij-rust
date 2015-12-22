package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubBase
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustCompositeElement

abstract class RustStubElementType<StubT, PsiT> : IStubElementType<StubT, PsiT>
    where StubT : StubBase<*>,
          PsiT  : RustCompositeElement {

    constructor(debugName: String) : super(debugName, RustLanguage)

    override fun indexStub(stub: StubT, sink: IndexSink) {

    }

    override fun getExternalId(): String = "rust.${super.toString()}"
}
