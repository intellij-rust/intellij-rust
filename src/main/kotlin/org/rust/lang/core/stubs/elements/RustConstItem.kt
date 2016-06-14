package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustConstItemElement
import org.rust.lang.core.psi.impl.RustConstItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustConstItemStubElementType : RustNamedStubElementType<RustConstItemStub, RustConstItemElement>("CONST_ITEM") {
    override fun createStub(psi: RustConstItemElement, parentStub: StubElement<*>?): RustConstItemStub =
        RustConstItemStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustConstItemStub): RustConstItemElement =
        RustConstItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustConstItemStub =
        RustConstItemStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustConstItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }
}


class RustConstItemStub : RustNamedElementStub<RustConstItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""))

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "")
}
