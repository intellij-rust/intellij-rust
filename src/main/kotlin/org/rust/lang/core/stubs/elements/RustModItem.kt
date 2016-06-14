package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.impl.RustModItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustModItemStubElementType : RustNamedStubElementType<RustModItemStub, RustModItemElement>("MOD_ITEM") {
    override fun createStub(psi: RustModItemElement, parentStub: StubElement<*>?): RustModItemStub =
        RustModItemStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustModItemStub): RustModItemElement =
        RustModItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustModItemStub =
        RustModItemStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustModItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }
}


class RustModItemStub : RustNamedElementStub<RustModItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""))

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "")
}
