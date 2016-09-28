package org.rust.lang.core.psi.impl

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.resolve.util.RustResolveUtil
import org.rust.lang.core.stubs.RustNamedElementStub

abstract class RustStubbedNamedElementImpl<StubT> : RustStubbedElementImpl<StubT>,
                                                    RustNamedElement
    where StubT : RustNamedElementStub<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    protected open val nameElement: PsiElement?
        get() = findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? {
        val stub = stub
        return if (stub != null) stub.name else nameElement?.text
    }

    override fun setName(name: String): PsiElement? {
        nameElement?.replace(RustElementFactory.createIdentifier(project, name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameElement ?: this

    override fun getTextOffset(): Int = nameElement?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation {
        val crateRoot = RustResolveUtil.getCrateRootModFor(this)
        val crateName = crateRoot?.containingFile?.virtualFile?.let {
            module?.cargoProject?.findTargetForFile(it)?.name
        }
        val mod = containingFile as RustFile
        val cratePath = mod.canonicalCratePath?.toString()
        val loc = if (crateName != null && cratePath != null) {
            "$crateName$cratePath"
        } else {
            mod.modName
        }
        return PresentationData(
            name, "(in $loc)", getIcon(0), null)
    }
}

