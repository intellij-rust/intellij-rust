package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustTypeItemElement
import org.rust.lang.core.psi.impl.RustTypeItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustTypeItemStubElementType : RustNamedStubElementType<RustTypeItemElementStub, RustTypeItemElement>("TYPE_ITEM") {
    override fun createStub(psi: RustTypeItemElement, parentStub: StubElement<*>?): RustTypeItemElementStub =
        RustTypeItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustTypeItemElementStub): RustTypeItemElement =
        RustTypeItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustTypeItemElementStub =
        RustTypeItemElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustTypeItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

}


class RustTypeItemElementStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : RustNamedElementStub<RustTypeItemElement>(parent, elementType, name ?: "", isPublic)
