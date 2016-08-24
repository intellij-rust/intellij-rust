package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.impl.RustModItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustModItemStubElementType : RustNamedStubElementType<RustModItemElementStub, RustModItemElement>("MOD_ITEM") {
    override fun createStub(psi: RustModItemElement, parentStub: StubElement<*>?): RustModItemElementStub =
        RustModItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustModItemElementStub): RustModItemElement =
        RustModItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustModItemElementStub =
        RustModItemElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustModItemElementStub, dataStream: StubOutputStream) =
        with(dataStream) {
            writeName(stub.name)
            writeBoolean(stub.isPublic)
        }
}


class RustModItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean
) : RustNamedElementStub<RustModItemElement>(parent, elementType, name ?: "", isPublic)
