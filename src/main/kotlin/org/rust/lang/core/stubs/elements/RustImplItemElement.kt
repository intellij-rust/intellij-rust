package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.impl.RustImplItemElementImpl
import org.rust.lang.core.stubs.RustElementStub
import org.rust.lang.core.stubs.RustStubElementType
import org.rust.lang.core.stubs.index.RustInherentImplIndex
import org.rust.lang.core.types.util.type


object RustImplItemStubElementType : RustStubElementType<RustImplItemElementStub, RustImplItemElement>("IMPL_ITEM") {
    override fun createStub(psi: RustImplItemElement, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this, psi.type?.type?.nominalTypeName)

    override fun createPsi(stub: RustImplItemElementStub): RustImplItemElement =
        RustImplItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this, dataStream.readName()?.string)

    override fun serialize(stub: RustImplItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.implNominalTypeName)
    }

    override fun indexStub(stub: RustImplItemElementStub, sink: IndexSink) {
        stub.implNominalTypeName?.let {
            sink.occurrence(RustInherentImplIndex.KEY, it)
        }
    }

}


class RustImplItemElementStub : RustElementStub<RustImplItemElement> {
    val implNominalTypeName: String?

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, typeName: String?)
    : super(parent, elementType) {
        this.implNominalTypeName = typeName
    }

}
