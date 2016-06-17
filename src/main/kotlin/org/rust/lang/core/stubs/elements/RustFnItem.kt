package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.impl.RustFnItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustFnItemStubElementType : RustNamedStubElementType<RustFnItemElementStub, RustFnItemElement>("FN_ITEM") {
    override fun createStub(psi: RustFnItemElement, parentStub: StubElement<*>?): RustFnItemElementStub =
        RustFnItemElementStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustFnItemElementStub): RustFnItemElement =
        RustFnItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustFnItemElementStub =
        RustFnItemElementStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustFnItemElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }
}


class RustFnItemElementStub : RustNamedElementStub<RustFnItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
