package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustVisibilityOwner
import org.rust.lang.core.stubs.RustNamedElementStub

abstract class RustStubbedNamedVisibilityOwnerElementImpl<StubT: RustNamedElementStub<*>>
    : RustStubbedNamedElementImpl<StubT>
    , RustVisibilityOwner {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = vis != null
}
