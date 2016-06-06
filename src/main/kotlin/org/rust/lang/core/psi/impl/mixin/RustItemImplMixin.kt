package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustItemElement
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.RustItemStub

abstract class RustItemImplMixin : RustStubbedNamedElementImpl<RustItemStub>
                                 , RustItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

}


