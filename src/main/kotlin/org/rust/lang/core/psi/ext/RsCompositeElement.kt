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
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace

interface RsElement : PsiElement {
    /**
     * Find parent module *in this file*. See [RsMod.super]
     */
    val containingMod: RsMod

    val crateRoot: RsMod?
}

val CARGO_WORKSPACE = Key.create<CargoWorkspace>("CARGO_WORKSPACE")
val RsElement.cargoWorkspace: CargoWorkspace?
    get() {
        val psiFile = contextualFile.originalFile
        psiFile.getUserData(CARGO_WORKSPACE)?.let { return it }
        val vFile = psiFile.virtualFile ?: return null
        return project.cargoProjects.findProjectForFile(vFile)?.workspace
    }


val RsElement.containingCargoTarget: CargoWorkspace.Target?
    get() {
        val ws = cargoWorkspace ?: return null
        val root = crateRoot ?: return null
        val file = root.contextualFile.originalFile.virtualFile ?: return null
        return ws.findTargetByCrateRoot(file)
    }

val RsElement.containingCargoPackage: CargoWorkspace.Package? get() = containingCargoTarget?.pkg

abstract class RsElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), RsElement {
    override val containingMod: RsMod
        get() = contextStrict()
            ?: error("Element outside of module: $text")

    final override val crateRoot: RsMod?
        get() = (context as? RsElement)?.crateRoot
}

abstract class RsStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, RsElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val containingMod: RsMod
        get() = contextStrict()
            ?: error("Element outside of module: $text")

    final override val crateRoot: RsMod?
        get() = (context as? RsElement)?.crateRoot

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}
