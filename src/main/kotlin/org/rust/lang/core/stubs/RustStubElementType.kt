package org.rust.lang.core.stubs

import com.intellij.psi.stubs.*
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustCompositeElement

abstract class RustStubElementType<StubT, PsiT>(
    debugName: String
) : IStubElementType<StubT, PsiT>(debugName, RustLanguage)

    where StubT : StubElement<PsiT>,
          PsiT  : RustCompositeElement {

    final override fun getExternalId(): String = "rust.${super.toString()}"

    companion object {
        fun StubInputStream.readNameAsString(): String? = readName()?.string
    }

    abstract class Trivial<StubT, PsiT>(
        debugName: String,
        private val stubCtor: (StubElement<*>?, IStubElementType<*, *>) -> StubT,
        private val psiCtor: (StubT, IStubElementType<*, *>) -> PsiT
    ) : RustStubElementType<StubT, PsiT>(debugName)

        where StubT : StubElement<PsiT>,
              PsiT  : RustCompositeElement {

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) = stubCtor(parentStub, this)

        override fun serialize(stub: StubT, dataStream: StubOutputStream) {
            // NOP
        }

        override fun createPsi(stub: StubT) = psiCtor(stub, this)

        override fun createStub(psi: PsiT, parentStub: StubElement<*>?) = stubCtor(parentStub, this)

        override fun indexStub(stub: StubT, sink: IndexSink) {
            // NOP
        }
    }
}
