package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustStaticItemElement
import org.rust.lang.core.psi.impl.RustStaticItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustStaticItemStubElementType : RustNamedStubElementType<RustStaticItemStub, RustStaticItemElement>("STATIC_ITEM") {
    override fun createStub(psi: RustStaticItemElement, parentStub: StubElement<*>?): RustStaticItemStub =
        RustStaticItemStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustStaticItemStub): RustStaticItemElement =
        RustStaticItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustStaticItemStub =
        RustStaticItemStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustStaticItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }
}


class RustStaticItemStub : RustNamedElementStub<RustStaticItemElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""))

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "")
}
