package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.stubs.elements.RustUseItemElementStub

abstract class RustUseItemImplMixin : RustStubbedElementImpl<RustUseItemElementStub>, RustUseItemElement {

    constructor (node: ASTNode) : super(node)
    constructor (stub: RustUseItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getName(): String? =
        stub?.alias ?: alias?.identifier?.text

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublicNonStubbed(this)
}
