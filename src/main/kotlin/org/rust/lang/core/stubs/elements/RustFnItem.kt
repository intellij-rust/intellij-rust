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

object RustFnItemStubElementType : RustNamedStubElementType<RustFnItemStub, RustFnItemElement>("FN_ITEM") {
    override fun createStub(psi: RustFnItemElement, parentStub: StubElement<*>?): RustFnItemStub =
        RustFnItemStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustFnItemStub): RustFnItemElement =
        RustFnItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustFnItemStub =
        RustFnItemStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustFnItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }
}


class RustFnItemStub : RustNamedElementStub<RustFnItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
