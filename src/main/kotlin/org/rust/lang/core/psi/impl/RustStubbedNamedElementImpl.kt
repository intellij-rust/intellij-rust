package org.rust.lang.core.psi.impl

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.crateRoot
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
        val crateName = crateRoot?.containingFile?.virtualFile?.let {
            module?.cargoProject?.findTargetForCrateRootFile(it)?.name
        }
        val mod = containingFile as RustFile
        val cratePath = mod.crateRelativePath?.toString()
        val loc = if (crateName != null && cratePath != null) {
            "$crateName$cratePath"
        } else {
            mod.modName
        }
        return PresentationData(
            name, "(in $loc)", getIcon(0), null)
    }
}

