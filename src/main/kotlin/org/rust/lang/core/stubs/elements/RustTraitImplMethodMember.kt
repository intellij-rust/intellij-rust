package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.impl.RustImplMethodMemberElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustImplMethodMemberStubElementType : RustNamedStubElementType<RustImplMethodMemberStub, RustImplMethodMemberElement>("IMPL_METHOD_MEMBER") {
    override fun createStub(psi: RustImplMethodMemberElement, parentStub: StubElement<*>?): RustImplMethodMemberStub =
        RustImplMethodMemberStub(parentStub, this, psi.name, psi.isPublic)

    override fun createPsi(stub: RustImplMethodMemberStub): RustImplMethodMemberElement =
        RustImplMethodMemberElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplMethodMemberStub =
        RustImplMethodMemberStub(parentStub, this, dataStream.readName(), dataStream.readBoolean())

    override fun serialize(stub: RustImplMethodMemberStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
    }
}


class RustImplMethodMemberStub : RustNamedElementStub<RustImplMethodMemberElement> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?, isPublic: Boolean)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), isPublic)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?, isPublic: Boolean)
    : super(parent, elementType, name ?: "", isPublic)
}
