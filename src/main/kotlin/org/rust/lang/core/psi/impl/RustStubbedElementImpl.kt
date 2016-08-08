package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustStubbedElementImpl<StubT : StubBase<*>> : StubBasedPsiElementBase<StubT>
                                                           , RustCompositeElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParent(): PsiElement = parentByStub

    override fun getReference(): RustReference? = null

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}
