/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace

interface RsCompositeElement : PsiElement {
    /**
     * Find parent module *in this file*. See [RsMod.super]
     */
    val containingMod: RsMod

    val crateRoot: RsMod?
}

val CARGO_WORKSPACE = Key.create<CargoWorkspace>("CARGO_WORKSPACE")
val RsCompositeElement.cargoWorkspace: CargoWorkspace?
    get() {
        val psiFile = containingFile.originalFile
        psiFile.getUserData(CARGO_WORKSPACE)?.let { return it }
        val vFile = psiFile.virtualFile ?: return null
        return project.cargoProjects.findProjectForFile(vFile)?.workspace
    }


val RsCompositeElement.containingCargoTarget: CargoWorkspace.Target?
    get() {
        val ws = cargoWorkspace ?: return null
        val root = crateRoot ?: return null
        val file = root.containingFile.originalFile.virtualFile ?: return null
        return ws.findTargetByCrateRoot(file)
    }

val RsCompositeElement.containingCargoPackage: CargoWorkspace.Package? get() = containingCargoTarget?.pkg

abstract class RsCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), RsCompositeElement {
    override val containingMod: RsMod
        get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RsMod::class.java)
            ?: error("Element outside of module: $text")

    final override val crateRoot: RsMod?
        get() = (context as? RsCompositeElement)?.crateRoot
}

abstract class RsStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, RsCompositeElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val containingMod: RsMod
        get() = PsiTreeUtil.getStubOrPsiParentOfType(this, RsMod::class.java)
            ?: error("Element outside of module: $text")

    final override val crateRoot: RsMod?
        get() = (context as? RsCompositeElement)?.crateRoot

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}
