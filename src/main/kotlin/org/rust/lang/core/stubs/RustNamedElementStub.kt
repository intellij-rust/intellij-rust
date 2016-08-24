package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.NamedStubBase
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RustNamedElement

abstract class RustNamedElementStub<PsiT : RustNamedElement>(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String,
    val isPublic: Boolean
) : NamedStubBase<PsiT>(parent, elementType, name)
