package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.util.io.StringRef
import org.rust.lang.core.psi.RustFnElement

abstract class RustFnElementStub<PsiT : RustFnElement> : RustNamedElementStub<PsiT> {
    class FnAttributes(
        val isAbstract: Boolean,
        val isStatic: Boolean,
        val isTest: Boolean
    )

    val attributes: FnAttributes

    constructor(parent: StubElement<*>?, elementType: IStubElementType<*, *>,
                name: String?, isPublic: Boolean, attributes: FnAttributes)
    : super(parent, elementType, name ?: "", isPublic) {
        this.attributes = attributes
    }
}

