package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.RustNamedElementStub

abstract class RustStubbedNamedElementImpl<StubT> : RustStubbedElementImpl<StubT>
                                                  , RustNamedElement
    where StubT: RustNamedElementStub<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    protected open val nameElement: PsiElement?
        get() = findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? = stub?.let { stub ->
        stub.name
    } ?: nameElement?.text

    override fun setName(name: String): PsiElement? {
        // sic!
        throw UnsupportedOperationException()
    }

    override fun getNavigationElement(): PsiElement = nameElement ?: this

    override fun getTextOffset(): Int = nameElement?.textOffset ?: super.getTextOffset()

}
