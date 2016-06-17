package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustTraitMethodMemberElement
import org.rust.lang.core.psi.impl.RustTraitMethodMemberElementImpl
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType

object RustTraitMethodMemberStubElementType : RustNamedStubElementType<RustTraitMethodMemberElementStub, RustTraitMethodMemberElement>("TRAIT_METHOD_MEMBER") {
    override fun createStub(psi: RustTraitMethodMemberElement, parentStub: StubElement<*>?): RustTraitMethodMemberElementStub =
        RustTraitMethodMemberElementStub(parentStub, this, psi.name)

    override fun createPsi(stub: RustTraitMethodMemberElementStub): RustTraitMethodMemberElement =
        RustTraitMethodMemberElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustTraitMethodMemberElementStub =
        RustTraitMethodMemberElementStub(parentStub, this, dataStream.readName())

    override fun serialize(stub: RustTraitMethodMemberElementStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
    }
}


class RustTraitMethodMemberElementStub : RustNamedElementStub<RustTraitMethodMemberElement> {
    // no visibility is allowed for trait members, so always store `false` here
    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef?)
    : super(parent, elementType, name ?: StringRef.fromNullableString(""), false)

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String?)
    : super(parent, elementType, name ?: "", false)
}

