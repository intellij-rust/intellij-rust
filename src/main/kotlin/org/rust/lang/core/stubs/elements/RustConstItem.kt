package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustConstItemElement
import org.rust.lang.core.psi.impl.RustConstItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustConstItemStubElementType : RustNamedStubElementType<RustConstItemElementStub, RustConstItemElement>("CONST_ITEM") {
    override fun createStub(psi: RustConstItemElement, parentStub: StubElement<*>?): RustConstItemElementStub =
        RustConstItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustConstItemElementStub): RustConstItemElement =
        RustConstItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustConstItemElementStub =
        RustConstItemElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustConstItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }
}


class RustConstItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean
) : RustNamedElementStub<RustConstItemElement>(parent, elementType, name ?: "", isPublic)
