package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.impl.RustImplItemElementImpl
import org.rust.lang.core.psi.impl.mixin.baseTypeName
import org.rust.lang.core.stubs.RustElementStub
import org.rust.lang.core.stubs.RustStubElementType
import org.rust.lang.core.stubs.index.RustInherentImplIndex


object RustImplItemStubElementType : RustStubElementType<RustImplItemElementStub, RustImplItemElement>("IMPL_ITEM") {
    override fun createStub(psi: RustImplItemElement, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this, psi.baseTypeName)

    override fun createPsi(stub: RustImplItemElementStub): RustImplItemElement =
        RustImplItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this, dataStream.readName()?.string)

    override fun serialize(stub: RustImplItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.baseTypeName)
    }

    override fun indexStub(stub: RustImplItemElementStub, sink: IndexSink) {
        stub.baseTypeName?.let {
            sink.occurrence(RustInherentImplIndex.KEY, it)
        }
    }

}


class RustImplItemElementStub : RustElementStub<RustImplItemElement> {
    val baseTypeName: String?

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, typeName: String?)
    : super(parent, elementType) {
        this.baseTypeName = typeName
    }

}
