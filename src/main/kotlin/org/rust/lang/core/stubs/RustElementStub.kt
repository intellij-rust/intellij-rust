package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RustCompositeElement

abstract class RustElementStub<PsiT : RustCompositeElement>: StubBase<PsiT> {
    constructor(parent: StubElement<*>?, elementType: IStubElementType<out StubElement<*>, *>?) : super(parent, elementType)
}
