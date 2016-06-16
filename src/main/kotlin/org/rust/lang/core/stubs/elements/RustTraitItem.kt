package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.impl.RustTraitItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustTraitItemStubElementType : RustNamedStubElementType<RustTraitItemStub, RustTraitItemElement>("TRAIT_ITEM") {
    override fun createStub(psi: RustTraitItemElement, parentStub: StubElement<*>?): RustTraitItemStub =
        RustTraitItemStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustTraitItemStub): RustTraitItemElement =
        RustTraitItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustTraitItemStub =
        RustTraitItemStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustTraitItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

}


class RustTraitItemStub : RustNamedElementStub<RustTraitItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
