package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustFnElement

abstract class RustStubElementType<StubT, PsiT>(
    debugName: String
) : IStubElementType<StubT, PsiT>(debugName, RustLanguage)
    where StubT : StubElement<PsiT>,
          PsiT  : RustCompositeElement {

    final override fun getExternalId(): String = "rust.${super.toString()}"

    companion object {
        val RustFnElement.fnAttributes: RustFnElementStub.FnAttributes
            get() = RustFnElementStub.FnAttributes(
                isAbstract,
                isStatic,
                isTest
            )

        fun StubOutputStream.writeFnAttributes(attributes: RustFnElementStub.FnAttributes) {
            writeBoolean(attributes.isAbstract)
            writeBoolean(attributes.isStatic)
            writeBoolean(attributes.isTest)
        }

        fun StubInputStream.readFnAttributes(): RustFnElementStub.FnAttributes = RustFnElementStub.FnAttributes(
            readBoolean(),
            readBoolean(),
            readBoolean()
        )
    }
}
