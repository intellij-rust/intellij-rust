/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.inspections.import.ImportCandidate
import org.rust.ide.inspections.import.ImportInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.borrowck.BorrowCheckContext
import org.rust.lang.core.types.borrowck.BorrowCheckResult
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.recursionGuard


private fun <T> RsInferenceContextOwner.createResult(value: T): Result<T> {
    val structureModificationTracker = project.rustStructureModificationTracker

    return when {
        // The case of injected language. Injected PSI don't have it's own event system, so can only
        // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
        // literal. If a user change the literal, we can only be notified that the literal is changed.
        // So we have to invalidate the cached value on any PSI change
        containingFile.virtualFile is VirtualFileWindow -> Result.create(value, PsiModificationTracker.MODIFICATION_COUNT)

        // CachedValueProvider.Result can accept a ModificationTracker as a dependency, so the
        // cached value will be invalidated if the modification counter is incremented.
        this is RsModificationTrackerOwner -> Result.create(value, structureModificationTracker, modificationTracker)

        else -> Result.create(value, structureModificationTracker)
    }
}

val RsTypeReference.type: Ty
    get() = recursionGuard(this, Computable { inferTypeReferenceType(this) })
        ?: TyUnknown

val RsTypeElement.lifetimeElidable: Boolean
    get() {
        val typeOwner = owner.parent

        val isAssociatedConstant = typeOwner is RsConstant && typeOwner.owner.isImplOrTrait

        return typeOwner !is RsFieldDecl && typeOwner !is RsTupleFieldDecl
            && typeOwner !is RsTypeAlias && !isAssociatedConstant
    }

private val TYPE_INFERENCE_KEY: Key<CachedValue<RsInferenceResult>> = Key.create("TYPE_INFERENCE_KEY")

val RsInferenceContextOwner.inference: RsInferenceResult
    get() = CachedValuesManager.getCachedValue(this, TYPE_INFERENCE_KEY) {
        val inferred = inferTypesIn(this)

        createResult(inferred)
    }

val PsiElement.inference: RsInferenceResult?
    get() = contextOrSelf<RsInferenceContextOwner>()?.inference

val RsPatBinding.type: Ty
    get() = inference?.getBindingType(this) ?: TyUnknown

val RsExpr.type: Ty
    get() = inference?.getExprType(this) ?: TyUnknown

val RsExpr.declaration: RsElement?
    get() = when (this) {
        is RsPathExpr -> path.reference.resolve()
        is RsCallExpr -> expr.declaration
        is RsStructLiteral -> path.reference.resolve()
        else -> null
    }

val RsTraitOrImpl.selfType: Ty
    get() = when (this) {
        is RsImplItem -> typeReference?.type ?: TyUnknown
        is RsTraitItem -> TyTypeParameter.self(this)
        else -> error("Unreachable")
    }

val RsExpr.cmt: Cmt?
    get() {
        val items = this.knownItems
        val lookup = ImplLookup(this.project, items)
        val inference = this.inference ?: return null
        return MemoryCategorizationContext(lookup, inference).processExpr(this)
    }

val RsExpr.isMutable: Boolean
    get() = cmt?.isMutable ?: Mutability.DEFAULT_MUTABILITY.isMut

private val BORROW_CHECKER_KEY: Key<CachedValue<BorrowCheckResult>> = Key.create("BORROW_CHECKER_KEY")

val RsInferenceContextOwner.borrowCheckResult: BorrowCheckResult?
    get() = CachedValuesManager.getCachedValue(this, BORROW_CHECKER_KEY) {
        val bccx = BorrowCheckContext.buildFor(this)
        val borrowCheckResult = bccx?.check()
        createResult(borrowCheckResult)
    }

fun RsNamedElement?.asTy(): Ty =
    (this as? RsTypeDeclarationElement)?.declaredType ?: TyUnknown

// Semantic signature of method is `ImportItem.canBeImported(mod: RsMod)`
// but in our case `mod` is always same and `mod` needs only to get set of its super mods
// so we pass `superMods` instead of `mod` for optimization
fun QualifiedNamedItem.canBeImported(superMods: LinkedHashSet<RsMod>): ImportInfo? {
    if (item !is RsVisible) return null

    val ourSuperMods = this.superMods ?: return null
    val parentMod = ourSuperMods.getOrNull(0) ?: return null

    // try to find latest common ancestor module of `parentMod` and `mod` in module tree
    // we need to do it because we can use direct child items of any super mod with any visibility
    val lca = ourSuperMods.find { it in superMods }
    val crateRelativePath = crateRelativePath ?: return null

    val (shouldBePublicMods, importInfo) = if (lca == null) {
        if (!isPublic) return null
        val target = containingCargoTarget ?: return null
        val externCrateMod = ourSuperMods.last()

        val externCrateWithDepth = superMods.withIndex().mapNotNull { (index, superMod) ->
            val externCrateItem = superMod.childrenOfType<RsExternCrateItem>()
                .find { it.reference.resolve() == externCrateMod } ?: return@mapNotNull null
            val depth = if (superMod.isCrateRoot) null else index
            externCrateItem to depth
        }.singleOrNull()

        val (externCrateName, needInsertExternCrateItem, depth) = if (externCrateWithDepth == null) {
            Triple(target.normName, true, null)
        } else {
            val (externCrateItem, depth) = externCrateWithDepth
            Triple(externCrateItem.nameWithAlias, false, depth)
        }

        val importInfo = ImportInfo.ExternCrateImportInfo(target, externCrateName,
            needInsertExternCrateItem, depth, crateRelativePath)
        ourSuperMods to importInfo
    } else {
        // if current item is direct child of some ancestor of `mod` then it can be not public
        if (parentMod == lca) return ImportInfo.LocalImportInfo(crateRelativePath)
        if (!isPublic) return null
        ourSuperMods.takeWhile { it != lca }.dropLast(1) to ImportInfo.LocalImportInfo(crateRelativePath)
    }
    return if (shouldBePublicMods.all { it.isPublic }) return importInfo else null
}

fun RsMod.importItem(candidate: ImportCandidate) = importItem(project, candidate, this)

fun importItem(
    project: Project,
    candidate: ImportCandidate,
    mod: RsMod
) {
    checkWriteAccessAllowed()
    val psiFactory = RsPsiFactory(project)
    // depth of `mod` relative to module with `extern crate` item
    // we uses this info to create correct relative use item path if needed
    var relativeDepth: Int? = null

    val isEdition2018 = mod.containingCargoTarget?.edition == CargoWorkspace.Edition.EDITION_2018
    val info = candidate.info
    // if crate of importing element differs from current crate
    // we need to add new extern crate item
    if (info is ImportInfo.ExternCrateImportInfo) {
        val target = info.target
        val crateRoot = mod.crateRoot
        val attributes = crateRoot?.stdlibAttributes ?: RsFile.Attributes.NONE
        when {
            // but if crate of imported element is `std` and there aren't `#![no_std]` and `#![no_core]`
            // we don't add corresponding extern crate item manually
            // because it will be done by compiler implicitly
            attributes == RsFile.Attributes.NONE && target.isStd -> AutoImportFix.Testmarks.autoInjectedStdCrate.hit()
            // if crate of imported element is `core` and there is `#![no_std]`
            // we don't add corresponding extern crate item manually for the same reason
            attributes == RsFile.Attributes.NO_STD && target.isCore -> AutoImportFix.Testmarks.autoInjectedCoreCrate.hit()
            else -> {
                if (info.needInsertExternCrateItem && !isEdition2018) {
                    crateRoot?.insertExternCrateItem(psiFactory, info.externCrateName)
                } else {
                    if (info.depth != null) {
                        AutoImportFix.Testmarks.externCrateItemInNotCrateRoot.hit()
                        relativeDepth = info.depth
                    }
                }
            }
        }
    }
    val prefix = when (relativeDepth) {
        null -> if (info is ImportInfo.LocalImportInfo && isEdition2018) "crate::" else ""
        0 -> "self::"
        else -> "super::".repeat(relativeDepth)
    }
    mod.insertUseItem(psiFactory, "$prefix${info.usePath}")
}

private fun RsMod.insertExternCrateItem(psiFactory: RsPsiFactory, crateName: String) {
    val externCrateItem = psiFactory.createExternCrateItem(crateName)
    val lastExternCrateItem = childrenOfType<RsExternCrateItem>().lastElement
    if (lastExternCrateItem != null) {
        addAfter(externCrateItem, lastExternCrateItem)
    } else {
        addBefore(externCrateItem, firstItem)
        addAfter(psiFactory.createNewline(), firstItem)
    }
}

private fun RsMod.insertUseItem(psiFactory: RsPsiFactory, usePath: String) {
    val useItem = psiFactory.createUseItem(usePath)
    val anchor = childrenOfType<RsUseItem>().lastElement ?: childrenOfType<RsExternCrateItem>().lastElement
    if (anchor != null) {
        val insertedUseItem = addAfter(useItem, anchor)
        if (anchor is RsExternCrateItem) {
            addBefore(psiFactory.createNewline(), insertedUseItem)
        }
    } else {
        addBefore(useItem, firstItem)
        addAfter(psiFactory.createNewline(), firstItem)
    }
}


private val RsElement.stdlibAttributes: RsFile.Attributes
    get() = (crateRoot?.containingFile as? RsFile)?.attributes ?: RsFile.Attributes.NONE
private val RsItemsOwner.firstItem: RsElement get() = itemsAndMacros.first { it !is RsAttr }
private val <T: RsElement> List<T>.lastElement: T? get() = maxBy { it.textOffset }

private val CargoWorkspace.Target.isStd: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.STD

private val CargoWorkspace.Target.isCore: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.CORE
