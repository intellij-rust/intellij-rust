package org.rust.lang.core.stubs.elements

import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustUseItemElementImpl
import org.rust.lang.core.resolve.indexes.RustAliasIndex
import org.rust.lang.core.stubs.RustElementStub
import org.rust.lang.core.stubs.RustStubElementType

object RustUseItemStubElementType : RustStubElementType<RustUseItemElementStub, RustUseItemElement>("USE_ITEM") {

    override fun createStub(psi: RustUseItemElement, parentStub: StubElement<*>?): RustUseItemElementStub? =
        RustUseItemElementStub(parentStub, this, psi.alias?.name)

    override fun createPsi(stub: RustUseItemElementStub): RustUseItemElement? =
        RustUseItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustUseItemElementStub =
        RustUseItemElementStub(parentStub, this, dataStream.readName()?.string)

    override fun serialize(stub: RustUseItemElementStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.alias)
    }

    override fun indexStub(stub: RustUseItemElementStub, sink: IndexSink) {
        stub.alias?.let {
            sink.occurrence(RustAliasIndex.KEY, it)
        }
    }

}

class RustUseItemElementStub : RustElementStub<RustUseItemElement> {

    val alias: String?

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, aliased: String?)
        : super(parent, elementType) {

        alias = aliased
    }

}
