package org.rust.lang.core.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustCompositeElement

class RustPlaceholderStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>)
    : StubBase<RustCompositeElement>(parent, elementType) {

    class Type<PsiT : RustCompositeElement>(
        debugName: String,
        private val psiCtor: (RustPlaceholderStub, IStubElementType<*, *>) -> PsiT
    ) : RustStubElementType<RustPlaceholderStub, PsiT>(debugName) {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?)
            = RustPlaceholderStub(parentStub, this)

        override fun serialize(stub: RustPlaceholderStub, dataStream: StubOutputStream) {
        }

        override fun createPsi(stub: RustPlaceholderStub) = psiCtor(stub, this)

        override fun createStub(psi: PsiT, parentStub: StubElement<*>?) = RustPlaceholderStub(parentStub, this)

        override fun indexStub(stub: RustPlaceholderStub, sink: IndexSink) {
        }
    }
}
