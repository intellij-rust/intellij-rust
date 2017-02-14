package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsLifetimeDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.stubs.RsLifetimeDeclStub

abstract class RsLifetimeDeclImplMixin : RsStubbedNamedElementImpl<RsLifetimeDeclStub>, RsLifetimeDecl {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsLifetimeDeclStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = quoteIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createQuoteIdentifier(name))
        return this
    }
}
