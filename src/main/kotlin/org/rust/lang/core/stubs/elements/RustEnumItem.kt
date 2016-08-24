package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.psi.impl.RustEnumItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustGotoClassIndex

object RustEnumItemStubElementType : RustNamedStubElementType<RustEnumItemElementStub, RustEnumItemElement>("ENUM_ITEM") {
    override fun createStub(psi: RustEnumItemElement, parentStub: StubElement<*>?): RustEnumItemElementStub =
        RustEnumItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustEnumItemElementStub): RustEnumItemElement =
        RustEnumItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustEnumItemElementStub =
        RustEnumItemElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustEnumItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

    override fun indexStub(stub: RustEnumItemElementStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        stub.name?.let { sink.occurrence(RustGotoClassIndex.KEY, it) }
    }

}


class RustEnumItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean
) : RustNamedElementStub<RustEnumItemElement>(parent, elementType, name ?: "", isPublic)
