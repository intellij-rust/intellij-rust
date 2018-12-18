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
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.rustFile
import org.rust.openapiext.toPsiFile

interface RsElement : PsiElement {
    /**
     * Find parent module *in this file*. See [RsMod.super]
     */
    val containingMod: RsMod

    val crateRoot: RsMod?
}

val CARGO_WORKSPACE = Key.create<CargoWorkspace>("CARGO_WORKSPACE")

val RsElement.cargoProject: CargoProject?
    get() = contextualFile.originalFile.cargoProject

val RsElement.cargoWorkspace: CargoWorkspace?
    get() {
        val psiFile = contextualFile.originalFile
        psiFile.getUserData(CARGO_WORKSPACE)?.let { return it }
        return psiFile.cargoProject?.workspace
    }

private val PsiFile.cargoProject: CargoProject?
    get() {
        val vFile = virtualFile ?: return null
        return project.cargoProjects.findProjectForFile(vFile)
    }


val RsElement.containingCargoTarget: CargoWorkspace.Target?
    get() {
        val ws = cargoWorkspace ?: return null
        val root = crateRoot ?: return null
        val file = root.contextualFile.originalFile.virtualFile ?: return null
        return ws.findTargetByCrateRoot(file)
    }

val RsElement.containingCargoPackage: CargoWorkspace.Package? get() = containingCargoTarget?.pkg

val PsiElement.isEdition2018: Boolean get() =
    contextOrSelf<RsElement>()?.containingCargoTarget?.edition == CargoWorkspace.Edition.EDITION_2018

/**
 * It is possible to match value with constant-like element, e.g.
 *      ```
 *      enum Kind { A }
 *      use Kind::A;
 *      match kind { A => ... } // `A` is a constant-like element, not a pat binding
 *      ```
 *
 * But there is no way to distinguish a pat binding from a constant-like element on syntax level,
 * so we resolve an item `A` first, and then use [isConstantLike] to check whether the element is constant-like or not.
 *
 * Constant-like element can be: real constant, static variable, and enum variant without fields.
 */
val RsElement.isConstantLike: Boolean
    get() = this is RsConstant || (this is RsEnumVariant && blockFields == null && tupleFields == null)

fun RsElement.findDependencyCrateRoot(dependencyName: String): RsFile? {
    return containingCargoPackage
        ?.findDependency(dependencyName)
        ?.crateRoot
        ?.toPsiFile(project)
        ?.rustFile
}

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
