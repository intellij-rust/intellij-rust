package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustItemElementImpl
import org.rust.lang.core.stubs.RustItemStub

abstract class RustUseItemImplMixin : RustItemElementImpl, RustUseItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

}

val RustUseItemElement.isStarImport: Boolean get() = mul != null
