package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.impl.RustModDeclItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustModDeclItemStubElementType : RustNamedStubElementType<RustModDeclItemStub, RustModDeclItemElement>("MOD_DECL_ITEM") {
    override fun createStub(psi: RustModDeclItemElement, parentStub: StubElement<*>?): RustModDeclItemStub =
        RustModDeclItemStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustModDeclItemStub): RustModDeclItemElement =
        RustModDeclItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustModDeclItemStub =
        RustModDeclItemStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustModDeclItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }
}


class RustModDeclItemStub : RustNamedElementStub<RustModDeclItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""))

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "")
}
