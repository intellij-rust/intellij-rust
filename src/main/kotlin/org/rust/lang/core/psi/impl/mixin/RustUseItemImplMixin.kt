package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.stubs.RsUseItemStub

abstract class RustUseItemImplMixin : RustStubbedElementImpl<RsUseItemStub>, RsUseItem {

    constructor (node: ASTNode) : super(node)
    constructor (stub: RsUseItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)
}

val RsUseItem.isStarImport: Boolean get() = stub?.isStarImport ?: (mul != null) // I hate operator precedence
