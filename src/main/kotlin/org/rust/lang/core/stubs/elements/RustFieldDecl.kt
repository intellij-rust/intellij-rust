package org.rust.lang.core.stubs.elements

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustFieldDeclElement
import org.rust.lang.core.psi.impl.RustFieldDeclElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustFieldDeclStubElementType : RustNamedStubElementType<RustFieldDeclElementStub, RustFieldDeclElement>("FIELD_DECL") {
    override fun createStub(psi: RustFieldDeclElement, parentStub: StubElement<*>?): RustFieldDeclElementStub =
        RustFieldDeclElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustFieldDeclElementStub): RustFieldDeclElement =
        RustFieldDeclElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustFieldDeclElementStub =
        RustFieldDeclElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readBoolean())

    override fun serialize(stub: RustFieldDeclElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

}


class RustFieldDeclElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean
) : RustNamedElementStub<RustFieldDeclElement>(parent, elementType, name ?: "", isPublic)
