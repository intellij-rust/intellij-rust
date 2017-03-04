package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.ext.RsCompositeElement

abstract class RsElementStub<PsiT : RsCompositeElement>(
    parent: StubElement<*>?, elementType: IStubElementType<out StubElement<*>, *>?
) : StubBase<PsiT>(parent, elementType)
