package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.psi.RustStructOrEnum
import org.rust.lang.core.psi.impl.RustEnumItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

object RustEnumItemStubElementType : RustNamedStubElementType<RustEnumItemStub, RustEnumItemElement>("ENUM_ITEM") {
    override fun createStub(psi: RustEnumItemElement, parentStub: StubElement<*>?): RustEnumItemStub =
        RustEnumItemStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustEnumItemStub): RustEnumItemElement =
        RustEnumItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustEnumItemStub =
        RustEnumItemStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustEnumItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }

    override val additionalIndexingKeys: Collection<StubIndexKey<String, RustStructOrEnum>> =
        listOf(RustStructOrEnumIndex.KEY)

}


class RustEnumItemStub : RustNamedElementStub<RustEnumItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""))

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "")
}
