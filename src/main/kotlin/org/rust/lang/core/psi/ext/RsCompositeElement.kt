/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.resolve.ref.RsReference

interface RsCompositeElement : PsiElement {
    /**
     * Find parent module *in this file*. See [RsMod.super]
     */
    val containingMod: RsMod
}

val RsCompositeElement.cargoWorkspace: CargoWorkspace? get() {
    // It's important to look the module for `containingFile` file
    // and not the element itself. Otherwise this will break for
    // elements in libraries.
    val module = ModuleUtilCore.findModuleForPsiElement(containingFile)
    return module?.cargoWorkspace
}


val RsCompositeElement.crateRoot: RsMod? get() {
    return if (this is RsFile) {
        val root = superMods.lastOrNull()
        if (root != null && root.isCrateRoot)
            root
        else
            null
    } else {
        (context as? RsCompositeElement)?.crateRoot
    }
}

val RsCompositeElement.containingCargoTarget: CargoWorkspace.Target? get() {
    val ws = cargoWorkspace ?: return null
    val root = crateRoot ?: return null
    val file = root.containingFile.originalFile.virtualFile ?: return null
    return ws.findTargetForCrateRootFile(file)
}

val RsCompositeElement.containingCargoPackage: CargoWorkspace.Package? get() = containingCargoTarget?.pkg

abstract class RsCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), RsCompositeElement {
    override fun getReference(): RsReference? = null

    override val containingMod: RsMod
        get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RsMod::class.java)
            ?: error("Element outside of module: $text")
}

abstract class RsStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, RsCompositeElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference? = null

    override val containingMod: RsMod
        get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RsMod::class.java)
            ?: error("Element outside of module: $text")

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}
