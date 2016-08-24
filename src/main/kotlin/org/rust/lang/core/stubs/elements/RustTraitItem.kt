package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.impl.RustTraitItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustGotoClassIndex

object RustTraitItemStubElementType : RustNamedStubElementType<RustTraitItemElementStub, RustTraitItemElement>("TRAIT_ITEM") {
    override fun createStub(psi: RustTraitItemElement, parentStub: StubElement<*>?): RustTraitItemElementStub =
        RustTraitItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustTraitItemElementStub): RustTraitItemElement =
        RustTraitItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustTraitItemElementStub =
        RustTraitItemElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustTraitItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

    override fun indexStub(stub: RustTraitItemElementStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        stub.name?.let { sink.occurrence(RustGotoClassIndex.KEY, it) }
    }

}


class RustTraitItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean
) : RustNamedElementStub<RustTraitItemElement>(parent, elementType, name ?: "", isPublic)
