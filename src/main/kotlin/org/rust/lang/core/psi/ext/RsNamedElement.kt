package org.rust.lang.core.psi.ext

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.stubs.RsNamedStub

interface RsNamedElement : RsCompositeElement, PsiNamedElement, NavigatablePsiElement


abstract class RsNamedElementImpl(node: ASTNode) : RsCompositeElementImpl(node),
                                                   RsNamedElement,
                                                   PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = findChildByType(IDENTIFIER)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}


abstract class RsStubbedNamedElementImpl<StubT> : RsStubbedElementImpl<StubT>,
                                                  RsNamedElement,
                                                  PsiNameIdentifierOwner
where StubT : RsNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement?
        = findChildByType(IDENTIFIER)

    override fun getName(): String? {
        val stub = stub
        return if (stub != null) stub.name else nameIdentifier?.text
    }

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation {
        val mod = containingMod
        return PresentationData(
            name, "(in ${mod.qualifiedName ?: mod.modName})", getIcon(0), null)
    }

    val containingMod: RsMod get() = (this as RsCompositeElement).containingMod
        ?: error("Rust inner element outside of the module")
}
