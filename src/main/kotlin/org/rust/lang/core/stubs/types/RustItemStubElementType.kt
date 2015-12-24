package org.rust.lang.core.stubs.types

import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.stubs.RustItemStub

abstract class RustItemStubElementType<PsiT: RustItem>(debugName: String)
    : RustStubElementType<RustItemStub, PsiT>(debugName) {

    override fun serialize(stub: RustItemStub, dataStream: StubOutputStream) =
        dataStream.writeName(stub.name)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustItemStub =
        RustItemStub(parentStub, this, dataStream.readName())

    override fun createStub(psi: PsiT, parentStub: StubElement<*>?): RustItemStub =
        RustItemStub(parentStub, this, psi.name ?: "")

}
