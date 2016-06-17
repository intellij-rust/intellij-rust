package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.psi.impl.RustEnumItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

object RustEnumItemStubElementType : RustNamedStubElementType<RustEnumItemElementStub, RustEnumItemElement>("ENUM_ITEM") {
    override fun createStub(psi: RustEnumItemElement, parentStub: StubElement<*>?): RustEnumItemElementStub =
        RustEnumItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustEnumItemElementStub): RustEnumItemElement =
        RustEnumItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustEnumItemElementStub =
        RustEnumItemElementStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustEnumItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

    override val additionalIndexingKeys: Collection<StubIndexKey<String, RustStructOrEnum>> =
        listOf(RustStructOrEnumIndex.KEY)

}


class RustEnumItemElementStub : RustNamedElementStub<RustEnumItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
