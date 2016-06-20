package org.rust.lang.core.stubs.elements

import com.intellij.psi.stubs.*
import com.intellij.util.io.StringRef
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
        RustFieldDeclElementStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustFieldDeclElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }

}


class RustFieldDeclElementStub : RustNamedElementStub<RustFieldDeclElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
