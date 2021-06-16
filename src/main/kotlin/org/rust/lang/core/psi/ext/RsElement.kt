/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.findDependency
import org.rust.lang.core.macros.findNavigationTargetIfMacroExpansion
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.createProcessor
import org.rust.lang.core.resolve.processLocalVariables
import org.rust.lang.core.resolve.processNestedScopesUpwards

interface RsElement : PsiElement {
    /**
     * Find parent module *in this file*. See [RsMod.super]
     */
    @JvmDefault
    val containingMod: RsMod
        get() = contextStrict<RsMod>()?.getOriginalOrSelf()
            ?: error("Element outside of module: $text")

    @JvmDefault
    val crateRoot: RsMod?
        get() = (contextualFile as? RsElement)?.crateRoot
}

val RsElement.cargoProject: CargoProject?
    get() = (contextualFile.originalFile as? RsFile)?.cargoProject

val RsElement.cargoWorkspace: CargoWorkspace?
    get() = (contextualFile.originalFile as? RsFile)?.cargoWorkspace

fun PsiFileSystemItem.findCargoProject(): CargoProject? {
    if (this is RsFile) return this.cargoProject
    val vFile = virtualFile ?: return null
    return project.cargoProjects.findProjectForFile(vFile)
}

fun PsiFileSystemItem.findCargoPackage(): CargoWorkspace.Package? {
    if (this is RsFile) return this.crate?.cargoTarget?.pkg
    val vFile = virtualFile ?: return null
    return project.cargoProjects.findPackageForFile(vFile)
}

val RsElement.containingCargoTarget: CargoWorkspace.Target?
    get() = containingCrate?.cargoTarget

val RsElement.containingCrate: Crate?
    get() = (contextualFile.originalFile as? RsFile)?.crate

val RsElement.containingCargoPackage: CargoWorkspace.Package? get() = containingCargoTarget?.pkg

val PsiElement.edition: CargoWorkspace.Edition?
    get() = contextOrSelf<RsElement>()?.containingCrate?.edition

val PsiElement.isAtLeastEdition2018: Boolean
    get() {
        val edition = edition ?: return false
        return edition >= CargoWorkspace.Edition.EDITION_2018
    }

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
    get() = this is RsConstant || (this is RsEnumVariant && isFieldless)

fun RsElement.findDependencyCrateRoot(dependencyName: String): RsFile? {
    return containingCrate
        ?.findDependency(dependencyName)
        ?.rootMod
}

abstract class RsElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), RsElement {
    override fun getNavigationElement(): PsiElement {
        return findNavigationTargetIfMacroExpansion() ?: super.getNavigationElement()
    }
}

abstract class RsStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, RsElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNavigationElement(): PsiElement {
        return findNavigationTargetIfMacroExpansion() ?: super.getNavigationElement()
    }

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}

fun RsElement.findInScope(name: String, ns: Set<Namespace>): PsiElement? {
    var resolved: PsiElement? = null
    val processor = createProcessor(name) { entry ->
        if (entry.name == name && entry.element != null) {
            resolved = entry.element
            true
        } else {
            false
        }
    }
    processNestedScopesUpwards(this, ns, processor)
    return resolved
}

fun RsElement.hasInScope(name: String, ns: Set<Namespace>): Boolean =
    findInScope(name, ns) != null

fun RsElement.getVisibleBindings(): Map<String, RsPatBinding> {
    val bindings = HashMap<String, RsPatBinding>()
    processLocalVariables(this) { variable ->
        variable.name?.let {
            bindings[it] = variable
        }
    }
    return bindings
}

/**
 * Delete the element along with a neighbour comma.
 * If a comma follows the element, it will be deleted.
 * Else if a comma precedes the element, it will be deleted.
 *
 * It is useful to remove elements that are parts of comma separated lists (parameters, arguments, use specks, ...).
 */
fun RsElement.deleteWithSurroundingComma() {
    val followingComma = getNextNonCommentSibling()
    if (followingComma?.elementType == RsElementTypes.COMMA) {
        followingComma?.delete()
    } else {
        val precedingComma = getPrevNonCommentSibling()
        if (precedingComma?.elementType == RsElementTypes.COMMA) {
            precedingComma?.delete()
        }
    }

    delete()
}

/**
 * Delete the element along with all surrounding whitespace and a single surrounding comma.
 * See [deleteWithSurroundingComma].
 */
fun RsElement.deleteWithSurroundingCommaAndWhitespace() {
    while (nextSibling?.isWhitespaceOrComment == true) {
        nextSibling?.delete()
    }
    while (prevSibling?.isWhitespaceOrComment == true) {
        prevSibling?.delete()
    }
    deleteWithSurroundingComma()
}

private val PsiElement.isWhitespaceOrComment
    get(): Boolean = this is PsiWhiteSpace || this is PsiComment
