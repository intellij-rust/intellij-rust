/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.hasDirectDependency
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TYPES_N_VALUES
import org.rust.lang.core.resolve.TraitImplSource
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.type
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

object ImportCandidatesCollector {

    /**
     * Returns a sequence of import candidates, after importing any of which it becomes possible to resolve the
     * path created from the `importingPathText`.
     *
     * @param importContext    The information about a path for which import candidates are looked for.
     * @param targetName        The name of searched import candidate.
     * @param importingPathText The text of the path that must resolve to candidates after import.
     * @param itemFilter        Additional filter for items before they become candidates.
     * @return the sequence of import candidates.
     */
    fun getImportCandidates(
        importContext: ImportContext,
        targetName: String,
        importingPathText: String,
        itemFilter: (QualifiedNamedItem) -> Boolean
    ): Sequence<ImportCandidate> {
        val project = importContext.project

        val explicitItems = RsNamedElementIndex.findElementsByName(project, targetName, importContext.scope)
            .asSequence()
            .filterIsInstance<RsQualifiedNamedElement>()
            .map { QualifiedNamedItem.ExplicitItem(it) }

        val reexportedItems = getReexportedItems(project, targetName, importContext.scope)

        return (explicitItems + reexportedItems)
            .filter(itemFilter)
            .flatMap { it.withModuleReexports(project).asSequence() }
            .mapNotNull { it.toImportCandidate(importContext.superMods) }
            .filterImportCandidates(importContext.attributes)
            // check that result after import can be resolved and resolved element is suitable
            // if no, don't add it in candidate list
            .filter { canBeResolvedToSuitableItem(importingPathText, importContext, it.info) }
    }

    private fun getReexportedItems(
        project: Project,
        targetName: String,
        scope: GlobalSearchScope
    ): Sequence<QualifiedNamedItem.ReexportedItem> {
        return RsReexportIndex.findReexportsByProducedName(project, targetName, scope)
            .asSequence()
            .filter { !it.isStarImport }
            .mapNotNull {
                val item = it.path?.reference?.resolve() as? RsQualifiedNamedElement ?: return@mapNotNull null
                QualifiedNamedItem.ReexportedItem.from(it, item)
            }
    }

    /**
     * Returns a sequence of import trait candidates for given [resolvedMethods].
     * After importing any of which it becomes possible to resolve the corresponding method call correctly.
     *
     * Returns null if there aren't traits to import at all. It can mean:
     * * given [resolvedMethods] don't refer to any trait
     * * if at least one trait related to [resolvedMethods] is already in scope
     */
    fun getImportCandidates(
        project: Project,
        scope: RsElement,
        resolvedMethods: List<MethodResolveVariant>
    ): Sequence<ImportCandidate>? {
        return getTraitImportCandidates(project, scope, resolvedMethods.map { it.source })
    }

    fun getTraitImportCandidates(
        project: Project,
        scope: RsElement,
        sources: List<TraitImplSource>
    ): Sequence<ImportCandidate>? {
        val traitsToImport = collectTraitsToImport(scope, sources)
            ?: return null
        val superMods = LinkedHashSet(scope.containingMod.superMods)
        val attributes = scope.stdlibAttributes

        val searchScope = RsWithMacrosProjectScope(project)
        return traitsToImport
            .asSequence()
            .map { QualifiedNamedItem.ExplicitItem(it) }
            .flatMap { traitItem ->
                val traitName = traitItem.itemName ?: return@flatMap sequenceOf(traitItem)
                val reexportedItems = getReexportedItems(project, traitName, searchScope)
                    .filter { it.item == traitItem.item }
                sequenceOf(traitItem) + reexportedItems
            }
            .flatMap { it.withModuleReexports(project).asSequence() }
            .mapNotNull { importItem -> importItem.toImportCandidate(superMods) }
            .filterImportCandidates(attributes)
    }

    private fun QualifiedNamedItem.toImportCandidate(superMods: LinkedHashSet<RsMod>): ImportCandidate? =
        canBeImported(superMods)?.let { ImportCandidate(this, it) }

    private fun collectTraitsToImport(
        scope: RsElement,
        sources: List<TraitImplSource>
    ): List<RsTraitItem>? {
        val traits = sources.mapNotNull { source ->
            if (source.isInherent) return null
            source.requiredTraitInScope
        }
        return if (traits.filterInScope(scope).isNotEmpty()) null else traits
    }

    // Semantic signature of method is `ImportItem.canBeImported(mod: RsMod)`
    // but in our case `mod` is always same and `mod` needs only to get set of its super mods
    // so we pass `superMods` instead of `mod` for optimization
    private fun QualifiedNamedItem.canBeImported(superMods: LinkedHashSet<RsMod>): ImportInfo? {
        check(superMods.isNotEmpty())
        if (item !is RsVisible) return null
        val ourCrate = containingCrate ?: return null
        val targetCrate = superMods.last().containingCrate ?: return null
        // filter out transitive dependencies
        if (targetCrate != ourCrate && !targetCrate.hasDirectDependency(ourCrate)) return null

        val ourSuperMods = this.superMods ?: return null
        val parentMod = ourSuperMods.getOrNull(0) ?: return null

        // try to find latest common ancestor module of `parentMod` and `mod` in module tree
        // we need to do it because we can use direct child items of any super mod with any visibility
        val lca = ourSuperMods.find { it.modItem in superMods }
        val crateRelativePath = crateRelativePath ?: return null

        val (shouldBePublicMods, importInfo) = if (lca == null) {
            if (!isPublic) return null
            val externCrateMod = ourSuperMods.last().modItem

            val externCrateWithDepth = superMods.withIndex().mapNotNull { (index, superMod) ->
                val externCrateItem = superMod.stubChildrenOfType<RsExternCrateItem>()
                    .find { it.reference.resolve() == externCrateMod } ?: return@mapNotNull null
                val depth = if (superMod.isCrateRoot) null else index
                externCrateItem to depth
            }.singleOrNull()

            val (externCrateName, needInsertExternCrateItem, depth) = if (externCrateWithDepth == null) {
                Triple(ourCrate.normName, true, null)
            } else {
                val (externCrateItem, depth) = externCrateWithDepth
                Triple(externCrateItem.nameWithAlias, false, depth)
            }

            val importInfo = ImportInfo.ExternCrateImportInfo(ourCrate, externCrateName,
                needInsertExternCrateItem, depth, crateRelativePath)
            ourSuperMods to importInfo
        } else {
            val targetMod = superMods.first()
            val relativePath = if (targetMod.isEdition2018) {
                "crate::$crateRelativePath"
            } else {
                crateRelativePath
            }
            // if current item is direct child of some ancestor of `mod` then it can be not public
            if (parentMod == lca) return ImportInfo.LocalImportInfo(relativePath)
            if (!isPublic) return null
            ourSuperMods.takeWhile { it != lca }.dropLast(1) to ImportInfo.LocalImportInfo(relativePath)
        }
        return if (shouldBePublicMods.all { it.modItem.isPublic }) return importInfo else null
    }

    private fun canBeResolvedToSuitableItem(
        importingPathName: String,
        context: ImportContext,
        info: ImportInfo
    ): Boolean {
        val externCrateName = if (info !is ImportInfo.ExternCrateImportInfo ||
            context.attributes == RsFile.Attributes.NONE && info.crate.isStd ||
            context.attributes == RsFile.Attributes.NO_STD && info.crate.isCore) {
            null
        } else {
            info.externCrateName
        }
        val path = RsCodeFragmentFactory(context.project).createPathInTmpMod(
            importingPathName,
            context.mod,
            context.pathParsingMode,
            TYPES_N_VALUES,
            info.usePath,
            externCrateName
        ) ?: return false
        val element = path.reference?.deepResolve() as? RsQualifiedNamedElement ?: return false
        if (!context.namespaceFilter(element)) return false

        // Looks like it's useless to access trait associated types directly (i.e. `Trait::Type`),
        // but methods can be used in UFCS and associated functions or constants can be accessed
        // it they have `Self` type in a signature

        if (element !is RsAbstractable || element.owner !is RsAbstractableOwner.Trait) return true
        if (element.canBeAccessedByTraitName) return true
        if (path.qualifier?.reference?.deepResolve() !is RsTraitItem) return true

        return false
    }

    private fun Sequence<ImportCandidate>.filterImportCandidates(
        attributes: RsFile.Attributes
    ): Sequence<ImportCandidate> = groupBy { it.qualifiedNamedItem.item }
        .map { (_, candidates) -> candidates }
        .asSequence()
        .flatMap { candidates -> filterForSingleItem(candidates, attributes).asSequence() }

    private fun filterForSingleItem(
        candidates: List<ImportCandidate>,
        fileAttributes: RsFile.Attributes
    ): List<ImportCandidate> {
        val candidatesWithPackage = mutableListOf<Pair<ImportCandidate, Crate>>()

        val stdlibCandidates = mutableListOf<Pair<ImportCandidate, Crate>>()

        for (candidate in candidates) {
            val crate = candidate.qualifiedNamedItem.containingCrate ?: continue
            val container = if (crate.origin == PackageOrigin.STDLIB) stdlibCandidates else candidatesWithPackage
            container += candidate to crate
        }

        candidatesWithPackage += filterStdlibCandidates(stdlibCandidates, fileAttributes)
        val pkgToCandidates = candidatesWithPackage.groupBy({ (_, pkg) -> pkg }, { (candidate, _) -> candidate })
        return pkgToCandidates.flatMap { (_, candidates) -> filterInPackage(candidates) }
    }

    private fun filterStdlibCandidates(
        stdlibCandidates: List<Pair<ImportCandidate, Crate>>,
        fileAttributes: RsFile.Attributes
    ): List<Pair<ImportCandidate, Crate>> {
        var hasImportWithSameAttributes = false
        val candidateToAttributes = stdlibCandidates.map { candidate ->
            val crate = candidate.second
            val attributes = when (crate.normName) {
                AutoInjectedCrates.STD -> RsFile.Attributes.NONE
                AutoInjectedCrates.CORE -> RsFile.Attributes.NO_STD
                else -> RsFile.Attributes.NO_CORE
            }
            hasImportWithSameAttributes = hasImportWithSameAttributes || attributes == fileAttributes
            candidate to attributes
        }

        val condition: (RsFile.Attributes) -> Boolean = if (hasImportWithSameAttributes) { attributes ->
            attributes == fileAttributes
        } else { attributes ->
            attributes < fileAttributes
        }
        return candidateToAttributes.mapNotNull { (candidate, attributes) ->
            if (condition(attributes)) candidate else null
        }
    }

    private fun filterInPackage(candidates: List<ImportCandidate>): List<ImportCandidate> {
        // If there is item reexport from some parent module of current import path
        // we want to drop this import candidate
        //
        // For example, in the following case
        //
        // mod_a -- mod_b -- Item
        //   \_______________/
        //
        // we have `mod_a::mod_b::Item` and `mod_a::Item` import candidates.
        // `mod_a::Item` is reexport of `Item` so we don't want to add `mod_a::mod_b::Item`
        // into final import list
        val candidatesWithSuperMods = candidates.mapNotNull {
            val superMods = it.qualifiedNamedItem.superMods ?: return@mapNotNull null
            it to superMods
        }
        val parents = candidatesWithSuperMods.mapTo(HashSet()) { (_, superMods) -> superMods[0] }
        return candidatesWithSuperMods.mapNotNull { (candidate, superMods) ->
            if (superMods.asSequence().drop(1).none { it in parents }) candidate else null
        }
    }
}

sealed class ImportInfo {

    abstract val usePath: String

    class LocalImportInfo(override val usePath: String) : ImportInfo()

    class ExternCrateImportInfo(
        val crate: Crate,
        val externCrateName: String,
        val needInsertExternCrateItem: Boolean,
        /**
         * Relative depth of importing path's module to module with extern crate item.
         * Used for creation of relative use path.
         *
         * For example, in the following case
         * ```rust
         * // lib.rs from bar crate
         * pub struct Bar {}
         * ```
         *
         * ```rust
         * // main.rs from our crate
         * mod foo {
         *     extern crate bar;
         *     mod baz {
         *          fn f(bar: Bar/*caret*/) {}
         *     }
         * }
         * ```
         *
         * relative depth of path `Bar` is `1`, so we should add `self::` prefix to use path.
         *
         * Can be null if extern crate item is absent or it is in crate root.
         */
        val depth: Int?,
        crateRelativePath: String
    ) : ImportInfo() {
        override val usePath: String = "$externCrateName::$crateRelativePath"
    }
}

data class ImportCandidate(val qualifiedNamedItem: QualifiedNamedItem, val info: ImportInfo)

/**
 * If function or constant is defined in a trait
 * ```rust
 * trait Trait {
 *     fn foo() {}
 * }
 * ```
 * it potentially can be accessed by the trait name `Trait::foo` only if there are `self` parameter or
 * `Self` type in the signature
 */
private val RsAbstractable.canBeAccessedByTraitName: Boolean
    get() {
        check(owner is RsAbstractableOwner.Trait)
        val type = when (this) {
            is RsFunction -> {
                if (selfParameter != null) return true
                type
            }
            is RsConstant -> typeReference?.type ?: return false
            else -> return false
        }
        return type.visitWith(object : TypeVisitor {
            override fun visitTy(ty: Ty): Boolean =
                if (ty is TyTypeParameter && ty.parameter is TyTypeParameter.Self) true else ty.superVisitWith(this)
        })
    }
