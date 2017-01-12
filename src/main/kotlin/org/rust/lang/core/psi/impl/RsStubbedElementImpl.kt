package org.rust.lang.core.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.resolve.ref.RsReference

abstract class RsStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>,
                                                              RsCompositeElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference? = null

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    // BACKCOMPAT: 2016.1
    // 2016.2 has shiny green stubs
    abstract class WithParent<StubT : StubElement<*>> : RsStubbedElementImpl<StubT> {
        constructor(node: ASTNode) : super(node)

        constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

        override fun getParent(): PsiElement = parentByStub
    }
}
