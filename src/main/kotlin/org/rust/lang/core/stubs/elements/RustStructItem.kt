package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.impl.RustStructItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

object RustStructItemStubElementType : RustNamedStubElementType<RustStructItemElementStub, RustStructItemElement>("STRUCT_ITEM") {
    override fun createStub(psi: RustStructItemElement, parentStub: StubElement<*>?): RustStructItemElementStub =
        RustStructItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustStructItemElementStub): RustStructItemElement =
        RustStructItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustStructItemElementStub =
        RustStructItemElementStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustStructItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

    override fun indexStub(stub: RustStructItemElementStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        stub.name?.let { sink.occurrence(RustStructOrEnumIndex.KEY, it) }
    }

}


class RustStructItemElementStub : RustNamedElementStub<RustStructItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
