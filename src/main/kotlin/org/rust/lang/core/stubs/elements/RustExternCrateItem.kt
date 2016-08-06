package org.rust.lang.core.stubs.elements

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.psi.impl.RustExternCrateItemElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustExternCrateItemStubElementType
    : RustNamedStubElementType<RustExternCrateItemElementStub, RustExternCrateItemElement>("EXTERN_CRATE_ITEM") {

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustExternCrateItemElementStub =
        RustExternCrateItemElementStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustExternCrateItemElementStub, dataStream: StubOutputStream) =
        with(dataStream) {
            writeName(stub.name)
            writeBoolean(stub.isPublic)
        }

    override fun createPsi(stub: RustExternCrateItemElementStub): RustExternCrateItemElement =
        RustExternCrateItemElementImpl(stub, this)

    override fun createStub(psi: RustExternCrateItemElement, parentStub: StubElement<*>?): RustExternCrateItemElementStub =
        RustExternCrateItemElementStub(parentStub, this, psi.name, psi.isPublic)
}

class RustExternCrateItemElementStub : RustNamedElementStub<RustExternCrateItemElement> {

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
        : super(parent, elementType, name ?: "", isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
        : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

}

