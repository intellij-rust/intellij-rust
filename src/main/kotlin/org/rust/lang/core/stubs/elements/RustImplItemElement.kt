package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.impl.RustImplItemElementImpl
import org.rust.lang.core.stubs.RustElementStub
import org.rust.lang.core.stubs.RustStubElementType


object RustImplItemStubElementType : RustStubElementType<RustImplItemElementStub, RustImplItemElement>("IMPL_ITEM") {
    override fun createStub(psi: RustImplItemElement, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this)

    override fun createPsi(stub: RustImplItemElementStub): RustImplItemElement =
        RustImplItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this)

    override fun serialize(stub: RustImplItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
    }

    override fun indexStub(stub: RustImplItemElementStub, sink: IndexSink) {
    }

}


class RustImplItemElementStub : RustElementStub<RustImplItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>)
    : super(parent, elementType)
}
