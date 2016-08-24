package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.impl.RustImplMethodMemberElementImpl
import org.rust.lang.core.stubs.RustFnElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustImplMethodMemberStubElementType : RustNamedStubElementType<RustImplMethodMemberElementStub, RustImplMethodMemberElement>("IMPL_METHOD_MEMBER") {
    override fun createStub(psi: RustImplMethodMemberElement, parentStub: StubElement<*>?): RustImplMethodMemberElementStub =
        RustImplMethodMemberElementStub(parentStub, this,
            psi.name, psi.isPublic, psi.fnAttributes)

    override fun createPsi(stub: RustImplMethodMemberElementStub): RustImplMethodMemberElement =
        RustImplMethodMemberElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplMethodMemberElementStub =
        RustImplMethodMemberElementStub(parentStub, this,
            dataStream.readNameAsString(), dataStream.readBoolean(), dataStream.readFnAttributes())

    override fun serialize(stub: RustImplMethodMemberElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
        writeFnAttributes(stub.attributes)
    }
}


class RustImplMethodMemberElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean,
    attributes: FnAttributes
) : RustFnElementStub<RustImplMethodMemberElement>(parent, elementType, name ?: "", isPublic, attributes)
