package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.impl.RustModDeclItemElementImpl
import org.rust.lang.core.psi.impl.mixin.pathAttribute
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustModDeclItemStubElementType : RustNamedStubElementType<RustModDeclElementItemStub, RustModDeclItemElement>("MOD_DECL_ITEM") {
    override fun createStub(psi: RustModDeclItemElement, parentStub: StubElement<*>?): RustModDeclElementItemStub =
        RustModDeclElementItemStub(parentStub, this, psi.name, psi.isPublic, psi.pathAttribute)

    override fun createPsi(stub: RustModDeclElementItemStub): RustModDeclItemElement =
        RustModDeclItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustModDeclElementItemStub =
        RustModDeclElementItemStub(parentStub, this,
            dataStream.readName(), dataStream.readBoolean(), dataStream.readUTFFast().let { if (it == "") null else it })

    override fun serialize(stub: RustModDeclElementItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
        writeUTFFast(stub.pathAttribute ?: "")
    }
}


class RustModDeclElementItemStub : RustNamedElementStub<RustModDeclItemElement> {
    val pathAttribute: String?

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>,
                name: StringRef?, isPublic: Boolean, pathAttribute: String?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic) {
        this.pathAttribute = pathAttribute
    }

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>,
                name: String?, isPublic: Boolean, pathAttribute: String?)
    : super(parent, elementType, name ?: "", isPublic) {
        this.pathAttribute = pathAttribute
    }
}
