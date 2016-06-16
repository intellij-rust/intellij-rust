package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.NamedStubBase
import com.intellij.psi.stubs.StubElement
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustNamedElement

abstract class RustNamedElementStub<PsiT : RustNamedElement> : NamedStubBase<PsiT> {
    val isPublic: Boolean

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: StringRef, isPublic: Boolean)
    : super(parent, elementType, name) {
        this.isPublic = isPublic
    }

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>, name: String, isPublic: Boolean)
    : super(parent, elementType, name) {
        this.isPublic = isPublic
    }
}
