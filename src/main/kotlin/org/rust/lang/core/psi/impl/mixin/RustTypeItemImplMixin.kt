package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustTypeItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.stubs.RustItemStub

abstract class RustTypeItemImplMixin : RustItemImpl, RustTypeItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}
