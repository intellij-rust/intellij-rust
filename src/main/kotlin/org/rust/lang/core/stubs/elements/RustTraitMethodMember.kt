package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustTraitMethodMemberElement
import org.rust.lang.core.psi.impl.RustTraitMethodMemberElementImpl
import org.rust.lang.core.stubs.RustFnElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustTraitMethodMemberStubElementType : RustNamedStubElementType<RustTraitMethodMemberElementStub, RustTraitMethodMemberElement>("TRAIT_METHOD_MEMBER") {
    override fun createStub(psi: RustTraitMethodMemberElement, parentStub: StubElement<*>?): RustTraitMethodMemberElementStub =
        RustTraitMethodMemberElementStub(parentStub, this,
            psi.name, psi.fnAttributes)

    override fun createPsi(stub: RustTraitMethodMemberElementStub): RustTraitMethodMemberElement =
        RustTraitMethodMemberElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustTraitMethodMemberElementStub =
        RustTraitMethodMemberElementStub(parentStub, this, dataStream.readNameAsString(), dataStream.readFnAttributes())

    override fun serialize(stub: RustTraitMethodMemberElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeFnAttributes(stub.attributes)
    }
}


// no visibility is allowed for trait members, so always store `false` for pub
class RustTraitMethodMemberElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    attributes: FnAttributes
) : RustFnElementStub<RustTraitMethodMemberElement>(parent, elementType, name ?: "", false, attributes)

