package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustConstItemElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.elements.RustConstItemElementStub

abstract class RustConstItemImplMixin : RustStubbedNamedElementImpl<RustConstItemElementStub>, RustConstItemElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RustConstItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this)
}
