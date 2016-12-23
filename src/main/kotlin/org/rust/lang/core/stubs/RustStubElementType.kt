package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustCompositeElement

abstract class RustStubElementType<StubT, PsiT>(
    debugName: String
) : IStubElementType<StubT, PsiT>(debugName, RustLanguage)

    where StubT : StubElement<PsiT>,
          PsiT  : RustCompositeElement {

    final override fun getExternalId(): String = "rust.${super.toString()}"

    companion object {
        fun StubInputStream.readNameAsString(): String? = readName()?.string
    }
}
