package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustStaticItemElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.elements.RustStaticItemElementStub

abstract class RustStaticItemImplMixin : RustStubbedNamedElementImpl<RustStaticItemElementStub>, RustStaticItemElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RustStaticItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this)
}
