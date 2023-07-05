/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.intellij.util.containers.addIfNotNull
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.findDependency
import org.rust.lang.core.crate.impl.FakeInvalidCrate
import org.rust.lang.core.macros.findNavigationTargetIfMacroExpansion
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.RsMacroBodyReferenceDelegateImpl

interface RsElement : PsiElement, UserDataHolderEx {
    /**
     * Find parent module *in this file*. See [RsMod.super]
     */
    val containingMod: RsMod
        get() = contextStrict<RsMod>()?.getOriginalOrSelf()
            ?: error("Element outside of module: $text")

    val crateRoot: RsMod?
        get() = containingRsFileSkippingCodeFragments?.crateRoot
}

val RsElement.cargoProject: CargoProject?
    get() = containingRsFileSkippingCodeFragments?.cargoProject

val RsElement.cargoWorkspace: CargoWorkspace?
    get() = containingRsFileSkippingCodeFragments?.cargoWorkspace

fun PsiFileSystemItem.findCargoProject(): CargoProject? {
    if (this is RsFile) return this.cargoProject
    val vFile = virtualFile ?: return null
    return project.cargoProjects.findProjectForFile(vFile)
}

fun PsiFileSystemItem.findCargoPackage(): CargoWorkspace.Package? {
    if (this is RsFile) return this.crate.cargoTarget?.pkg
    val vFile = virtualFile ?: return null
    return project.cargoProjects.findPackageForFile(vFile)
}

val RsElement.containingCargoTarget: CargoWorkspace.Target?
    get() = containingCrate.cargoTarget

val RsElement.containingCrate: Crate
    get() = containingRsFileSkippingCodeFragments?.crate ?: FakeInvalidCrate(project)

val RsElement.containingCargoPackage: CargoWorkspace.Package? get() = containingCargoTarget?.pkg

val RsElement.containingModOrSelf: RsMod get() = (this as? RsMod) ?: containingMod

val PsiElement.edition: Edition?
    get() = contextOrSelf<RsElement>()?.containingCrate?.edition

val PsiElement.isAtLeastEdition2018: Boolean
    get() = isAtLeastEdition(Edition.EDITION_2018)

val PsiElement.isAtLeastEdition2021: Boolean
    get() = isAtLeastEdition(Edition.EDITION_2021)

fun PsiElement.isAtLeastEdition(ed: Edition) = (edition ?: Edition.DEFAULT) >= ed

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
    get() = this is RsConstant || (this is RsFieldsOwner && isFieldless)

val RsElement.isInAsyncContext: Boolean
    get() {
        for (context in contexts) {
            when (context) {
                is RsBlockExpr -> if (context.isAsync) return true
                is RsFunctionOrLambda -> return context.isAsync
            }
        }
        return false
    }

fun RsElement.findDependencyCrateRoot(dependencyName: String): RsFile? {
    return containingCrate
        .findDependency(dependencyName)
        ?.rootMod
}

abstract class RsElementImpl(type: IElementType) : CompositePsiElement(type), RsElement {

    override fun getNavigationElement(): PsiElement {
        return findNavigationTargetIfMacroExpansion() ?: super.getNavigationElement()
    }

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}

abstract class RsLazyParseableElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), RsElement {

    override fun getNavigationElement(): PsiElement {
        return findNavigationTargetIfMacroExpansion() ?: super.getNavigationElement()
    }

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
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
    return pickFirstResolveVariant(name) {
        processNestedScopesUpwards(this, ns, it)
    }
}

fun RsElement.getLocalVariableVisibleBindings(): Map<String, RsPatBinding> {
    val bindings = HashMap<String, RsPatBinding>()
    processLocalVariables(this) { variable ->
        variable.name?.let {
            bindings[it] = variable
        }
    }
    return bindings
}

fun RsElement.getAllVisibleBindings(): Set<String> {
    return collectNames {
        processNestedScopesUpwards(this, VALUES, it)
    }
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
    val toDelete = rightSiblings.takeWhile { it.isWhitespaceOrComment } +
        leftSiblings.takeWhile { it.isWhitespaceOrComment }
    toDelete.forEach {
        it.delete()
    }

    deleteWithSurroundingComma()
}

/**
 * Delete the element along with a neighbour plus signs.
 * Same as deleteWithSurroundingComma but for "+" instead of ",".
 *
 * It's useful for removing parts of trait bounds.
 */
fun RsElement.deleteWithSurroundingPlus() {
    val followingPlus = getNextNonCommentSibling()
    if (followingPlus?.elementType == RsElementTypes.PLUS) {
        followingPlus?.delete()
    } else {
        val precedingPlus = getPrevNonCommentSibling()
        if (precedingPlus?.elementType == RsElementTypes.PLUS) {
            precedingPlus?.delete()
        }
    }
    delete()
}

fun RsElement.deleteWithPreviousWhitespace() {
    val whitespace = prevSibling as? PsiWhiteSpace
    if (whitespace != null) {
        // separate deletion of [whitespace] can lead to addition of new space
        parent.deleteChildRange(whitespace, this)
    } else {
        delete()
    }
}

private val PsiElement.isWhitespaceOrComment
    get(): Boolean = this is PsiWhiteSpace || this is PsiComment

fun RsElement.searchReferences(scope: SearchScope? = null): Query<PsiReference> {
    return if (scope == null) {
        ReferencesSearch.search(this)
    } else {
        ReferencesSearch.search(this, scope)
    }
}

fun RsElement.searchReferencesAfterExpansion(): Query<PsiReference> {
    val query = if (this is RsPatBinding) {
        val owner = PsiTreeUtil.getContextOfType(this,
            RsBlock::class.java,
            RsFunction::class.java,
            RsLambdaExpr::class.java
        ) ?: containingFile
        ReferencesSearch.search(this, LocalSearchScope(owner), /*ignoreAccessScope=*/ true)
    } else {
        searchReferences()
    }
    return query.flatMapping {
        if (it is RsMacroBodyReferenceDelegateImpl) {
            CollectionQuery(it.delegates)
        } else {
            CollectionQuery(listOf(it))
        }
    }
}

/**
 * struct Foo {}
 * ~~~~~~~~~~~~~ this
 * impl Foo {
 *     fn new() -> Self { ... }
 *                 ~~~~ this resolves to impl, so usual [searchReferences] will not find it
 * }
 */
fun RsElement.searchReferencesWithSelf(): List<PsiReference> {
    val references = ReferencesSearch.search(this).toMutableList()
    val searchHelper = PsiSearchHelper.getInstance(project)
    references += references.flatMap {
        it.findSelfReferences(searchHelper) ?: emptyList()
    }
    return references
}

/**
 * impl Foo {
 *      ~~~~ this
 *     fn new() -> Self { ... }
 *                 ~~~~ result
 * }
 */
private fun PsiReference.findSelfReferences(searchHelper: PsiSearchHelper): List<PsiReference>? {
    val implPath = element as? RsPath ?: return null
    val implType = implPath.parent as? RsPathType ?: return null
    val impl = implType.parent as? RsImplItem ?: return null
    if (impl.typeReference != implType) return null

    val result = mutableListOf<PsiReference>()
    val processor = TextOccurenceProcessor run@{ element, offset ->
        if (offset != 0) return@run true
        val path = (element.parent as? RsPath)?.takeIf { it.cself == element } ?: return@run true
        result.addIfNotNull(path.reference.takeIf { it?.resolve() == impl })
        true
    }
    val searchScope = LocalSearchScope(impl)
    searchHelper.processElementsWithWord(processor, searchScope, "Self", UsageSearchContext.IN_CODE, true, false)
    return result
}
