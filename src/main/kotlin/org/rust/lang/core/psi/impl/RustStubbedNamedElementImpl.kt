package org.rust.lang.core.psi.impl

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RustNamedElementStub

abstract class RustStubbedNamedElementImpl<StubT> : RustStubbedElementImpl<StubT>,
                                                    RustNamedElement,
                                                    PsiNameIdentifierOwner
where StubT : RustNamedElementStub<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement?
        = findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? {
        val stub = stub
        return if (stub != null) stub.name else nameIdentifier?.text
    }

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RustPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation {
        val mod = containingMod ?: containingFile as RustFile
        return PresentationData(
            name, "(in ${mod.qualifiedName ?: mod.modName})", getIcon(0), null)
    }
}

