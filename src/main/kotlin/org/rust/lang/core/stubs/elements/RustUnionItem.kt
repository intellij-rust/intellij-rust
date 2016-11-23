package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustUnionItemElement
import org.rust.lang.core.psi.impl.RustUnionItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustGotoClassIndex

object RustUnionItemStubElementType : RustNamedStubElementType<RustUnionItemElementStub, RustUnionItemElement>("UNION_ITEM") {
    override fun createStub(psi: RustUnionItemElement, parentStub: StubElement<*>?): RustUnionItemElementStub =
        RustUnionItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustUnionItemElementStub): RustUnionItemElement =
        RustUnionItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustUnionItemElementStub =
        RustUnionItemElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustUnionItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

    override fun indexStub(stub: RustUnionItemElementStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        stub.name?.let { sink.occurrence(RustGotoClassIndex.KEY, it) }
    }

}


class RustUnionItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean
) : RustNamedElementStub<RustUnionItemElement>(parent, elementType, name ?: "", isPublic)
