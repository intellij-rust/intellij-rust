package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsLabelDeclStub

abstract class RsLabelDeclImplMixin : RsStubbedNamedElementImpl<RsLabelDeclStub>, RsLabelDecl {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsLabelDeclStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getName(): String = lifetime.text
}
