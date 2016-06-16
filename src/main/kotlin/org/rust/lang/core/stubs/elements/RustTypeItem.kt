package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustTypeItemElement
import org.rust.lang.core.psi.impl.RustTypeItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustTypeItemStubElementType : RustNamedStubElementType<RustTypeItemStub, RustTypeItemElement>("TYPE_ITEM") {
    override fun createStub(psi: RustTypeItemElement, parentStub: StubElement<*>?): RustTypeItemStub =
        RustTypeItemStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustTypeItemStub): RustTypeItemElement =
        RustTypeItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustTypeItemStub =
        RustTypeItemStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustTypeItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

}


class RustTypeItemStub : RustNamedElementStub<RustTypeItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
