package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RustCompositeElement

abstract class RustElementStub<PsiT : RustCompositeElement>(
    parent: StubElement<*>?, elementType: IStubElementType<out StubElement<*>, *>?
) : StubBase<PsiT>(parent, elementType)
