package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.psi.impl.RustStructItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

object RustStructItemStubElementType : RustNamedStubElementType<RustStructItemStub, RustStructItemElement>("STRUCT_ITEM") {
    override fun createStub(psi: RustStructItemElement, parentStub: StubElement<*>?): RustStructItemStub =
        RustStructItemStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustStructItemStub): RustStructItemElement =
        RustStructItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustStructItemStub =
        RustStructItemStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustStructItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }

    override val additionalIndexingKeys: Collection<StubIndexKey<String, RustStructOrEnum>> =
        listOf(RustStructOrEnumIndex.KEY)

}


class RustStructItemStub : RustNamedElementStub<RustStructItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""))

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "")
}
