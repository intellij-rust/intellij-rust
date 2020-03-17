/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.search.RsWithMacrosProjectScope
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TYPES_N_VALUES
import org.rust.lang.core.resolve.TraitImplSource
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.type
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.runWriteCommandAction

class AutoImportFix(element: RsElement) : LocalQuickFixOnPsiElement(element), HighPriorityAction {

    private var isConsumed: Boolean = false

    override fun getFamilyName(): String = NAME
    override fun getText(): String = familyName

    public override fun isAvailable(): Boolean = super.isAvailable() && !isConsumed

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        invoke(project)
    }

    fun invoke(project: Project) {
        val element = startElement as? RsElement ?: return
        val (_, candidates) = when (element) {
            is RsPath -> findApplicableContext(project, element) ?: return
            is RsMethodCall -> findApplicableContext(project, element) ?: return
            else -> return
        }

        if (candidates.size == 1) {
            project.runWriteCommandAction {
                candidates.first().import(element)
            }
        } else {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
                chooseItemAndImport(project, it, candidates, element)
            }
        }
        isConsumed = true
    }

    private fun chooseItemAndImport(
        project: Project,
        dataContext: DataContext,
        items: List<ImportCandidate>,
        context: RsElement
    ) {
        showItemsToImportChooser(project, dataContext, items) { selectedValue ->
            project.runWriteCommandAction {
                selectedValue.import(context)
            }
        }
    }

    companion object {

        const val NAME = "Import"

        fun findApplicableContext(project: Project, path: RsPath): Context<RsPath>? {
            val reference = path.reference ?: return null
            val basePath = path.basePath()

            val isBasePathResolved = TyPrimitive.fromPath(basePath) != null || reference.multiResolve().isNotEmpty()

            if (isBasePathResolved) {
                // Despite the fact that path is (multi)resolved by our resolve engine, it can be unresolved from
                // the view of the rust compiler. Specifically we resolve associated items even if corresponding
                // trait is not in the scope, so here we suggest importing such traits

                return findApplicableContextForAssocItemPath(path, project)
            }

            if (path.ancestorStrict<RsUseSpeck>() != null) {
                // Don't try to import path in use item
                Testmarks.pathInUseItem.hit()
                return Context(basePath, emptyList())
            }

            val isNameInScope = path.hasInScope(basePath.referenceName, TYPES_N_VALUES)
            if (isNameInScope) {
                // Don't import names that are already in scope but cannot be resolved
                // because namespace of psi element prevents correct name resolution.
                // It's possible for incorrect or incomplete code like "let map = HashMap"
                Testmarks.nameInScope.hit()
                return Context(basePath, emptyList())
            }

            val candidates = getImportCandidates(ImportContext.from(project, path, false), basePath.referenceName, path.text) {
                path != basePath || !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers)
            }.toList()

            return Context(basePath, candidates)
        }

        fun findApplicableContext(project: Project, methodCall: RsMethodCall): Context<Unit>? {
            val results = methodCall.inference?.getResolvedMethod(methodCall) ?: emptyList()
            if (results.isEmpty()) return Context(Unit, emptyList())
            val candidates = getImportCandidates(project, methodCall, results)?.toList() ?: return null
            return Context(Unit, candidates)
        }

        /** Import traits for type-related UFCS method calls and assoc items */
        private fun findApplicableContextForAssocItemPath(path: RsPath, project: Project): Context<RsPath>? {
            val parent = path.parent as? RsPathExpr ?: return null
            val resolved = path.inference?.getResolvedPath(parent) ?: return null
            val sources = resolved.map {
                if (it !is ResolvedPath.AssocItem) return null
                it.source
            }
            val candidates = getTraitImportCandidates(project, path, sources)?.toList() ?: return null
            return Context(path, candidates)
        }

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
            return RsReexportIndex.findReexportsByName(project, targetName, scope)
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

        private fun getTraitImportCandidates(
            project: Project,
            scope: RsElement,
            sources: List<TraitImplSource>
        ): Sequence<ImportCandidate>? {
            val traitsToImport = collectTraitsToImport(scope, sources) ?: return null
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
            val target = containingCargoTarget ?: return null
            // filter out transitive dependencies
            if (target.pkg.origin == PackageOrigin.TRANSITIVE_DEPENDENCY) return null

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
                    Triple(target.normName, true, null)
                } else {
                    val (externCrateItem, depth) = externCrateWithDepth
                    Triple(externCrateItem.nameWithAlias, false, depth)
                }

                val importInfo = ImportInfo.ExternCrateImportInfo(target, externCrateName,
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
                context.attributes == RsFile.Attributes.NONE && info.target.isStd ||
                context.attributes == RsFile.Attributes.NO_STD && info.target.isCore) {
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
            val candidatesWithPackage = mutableListOf<Pair<ImportCandidate, CargoWorkspace.Package>>()

            val stdlibCandidates = mutableListOf<Pair<ImportCandidate, CargoWorkspace.Package>>()

            for (candidate in candidates) {
                val pkg = candidate.qualifiedNamedItem.containingCargoTarget?.pkg ?: continue
                val container = if (pkg.origin == PackageOrigin.STDLIB) stdlibCandidates else candidatesWithPackage
                container += candidate to pkg
            }

            candidatesWithPackage += filterStdlibCandidates(stdlibCandidates, fileAttributes)
            val pkgToCandidates = candidatesWithPackage.groupBy({ (_, pkg) -> pkg }, { (candidate, _) -> candidate })
            return pkgToCandidates.flatMap { (_, candidates) -> filterInPackage(candidates) }
        }

        private fun filterStdlibCandidates(
            stdlibCandidates: List<Pair<ImportCandidate, CargoWorkspace.Package>>,
            fileAttributes: RsFile.Attributes
        ): List<Pair<ImportCandidate, CargoWorkspace.Package>> {
            var hasImportWithSameAttributes = false
            val candidateToAttributes = stdlibCandidates.map { candidate ->
                val pkg = candidate.second
                val attributes = when (pkg.normName) {
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

    data class Context<T>(
        val data: T,
        val candidates: List<ImportCandidate>
    )

    object Testmarks {
        val autoInjectedStdCrate = Testmark("autoInjectedStdCrate")
        val autoInjectedCoreCrate = Testmark("autoInjectedCoreCrate")
        val pathInUseItem = Testmark("pathInUseItem")
        val externCrateItemInNotCrateRoot = Testmark("externCrateItemInNotCrateRoot")
        val nameInScope = Testmark("nameInScope")
        val doctestInjectionImport = Testmark("doctestInjectionImport")
        val insertNewLineBeforeUseItem = Testmark("insertNewLineBeforeUseItem")
    }
}

sealed class ImportInfo {

    abstract val usePath: String

    class LocalImportInfo(override val usePath: String) : ImportInfo()

    class ExternCrateImportInfo(
        val target: CargoWorkspace.Target,
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

private fun RsPath.namespaceFilter(isCompletion: Boolean): (RsQualifiedNamedElement) -> Boolean = when (context) {
    is RsTypeElement -> { e ->
        when (e) {
            is RsEnumItem,
            is RsStructItem,
            is RsTraitItem,
            is RsTypeAlias -> true
            else -> false
        }
    }
    is RsPathExpr -> { e ->
        when (e) {
            is RsEnumItem -> isCompletion
            // TODO: take into account fields type
            is RsFieldsOwner,
            is RsConstant,
            is RsFunction -> true
            else -> false
        }
    }
    is RsTraitRef -> { e -> e is RsTraitItem }
    is RsStructLiteral -> { e -> e is RsFieldsOwner && e.blockFields != null }
    is RsPatBinding -> { e ->
        when (e) {
            is RsEnumItem,
            is RsEnumVariant,
            is RsStructItem,
            is RsTypeAlias,
            is RsConstant,
            is RsFunction -> true
            else -> false
        }
    }
    else -> { _ -> true }
}

/**
 * Inserts a use declaration to the mod where [context] located for importing the selected candidate ([this]).
 * This action requires write access.
 */
fun ImportCandidate.import(context: RsElement) {
    checkWriteAccessAllowed()
    val psiFactory = RsPsiFactory(context.project)
    // depth of `mod` relative to module with `extern crate` item
    // we uses this info to create correct relative use item path if needed
    var relativeDepth: Int? = null

    val isEdition2018 = context.isEdition2018
    val info = info
    // if crate of importing element differs from current crate
    // we need to add new extern crate item
    if (info is ImportInfo.ExternCrateImportInfo) {
        val target = info.target
        val crateRoot = context.crateRoot
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
        null -> ""
        0 -> "self::"
        else -> "super::".repeat(relativeDepth)
    }

    val insertionScope = if (context.isDoctestInjection) {
        // In doctest injections all our code is located inside one invisible (main) function.
        // If we try to change PSI outside of that function, we'll take a crash.
        // So here we limit the module search with the last function (and never inert to an RsFile)
        AutoImportFix.Testmarks.doctestInjectionImport.hit()
        val scope = context.ancestors.find { it is RsMod && it !is RsFile }
            ?: context.ancestors.findLast { it is RsFunction }
        ((scope as? RsFunction)?.block ?: scope) as RsItemsOwner
    } else {
        context.containingMod
    }
    insertionScope.insertUseItem(psiFactory, "$prefix${info.usePath}")
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

private fun RsItemsOwner.insertUseItem(psiFactory: RsPsiFactory, usePath: String) {
    val useItem = psiFactory.createUseItem(usePath)
    if (tryGroupWithOtherUseItems(psiFactory, useItem)) return
    val anchor = childrenOfType<RsUseItem>().lastElement ?: childrenOfType<RsExternCrateItem>().lastElement
    if (anchor != null) {
        val insertedUseItem = addAfter(useItem, anchor)
        if (anchor is RsExternCrateItem || isDoctestInjection) {
            // Formatting is disabled in injections, so we have to add new line manually
            AutoImportFix.Testmarks.insertNewLineBeforeUseItem.hit()
            addBefore(psiFactory.createNewline(), insertedUseItem)
        }
    } else {
        addBefore(useItem, firstItem)
        addAfter(psiFactory.createNewline(), firstItem)
    }
}

private fun RsItemsOwner.tryGroupWithOtherUseItems(psiFactory: RsPsiFactory, newUseItem: RsUseItem): Boolean {
    val newParentPath = newUseItem.parentPath ?: return false
    val newImportingName = newUseItem.importingNames?.singleOrNull() ?: return false
    return childrenOfType<RsUseItem>().any { it.tryGroupWith(psiFactory, newParentPath, newImportingName) }
}

private fun RsUseItem.tryGroupWith(
    psiFactory: RsPsiFactory,
    newParentPath: List<String>,
    newImportingName: String
): Boolean {
    if (vis != null || outerAttrList.isNotEmpty() || useSpeck?.isStarImport == true) return false
    val parentPath = parentPath ?: return false
    if (parentPath != newParentPath) return false
    val importingNames = importingNames ?: return false
    if (importingNames.contains(newImportingName)) return true
    val newUsePath = parentPath.joinToString("::", postfix = "::") +
        (importingNames + newImportingName).joinToString(", ", "{", "}")
    val newUseSpeck = psiFactory.createUseSpeck(newUsePath)
    useSpeck?.replace(newUseSpeck)
    return true
}

private val RsUseItem.parentPath: List<String>?
    get() {
        val path = pathAsList ?: return null
        return if (useSpeck?.useGroup != null) path else path.dropLast(1)
    }

private val RsUseItem.importingNames: Set<String>?
    get() {
        if (useSpeck?.isStarImport == true) return null
        val path = pathAsList ?: return null
        val groupedNames = useSpeck?.useGroup?.useSpeckList?.asSequence()?.map { it.text }?.toSet()
        val lastName = path.lastOrNull()
        val alias = useSpeck?.alias?.identifier?.text
        return when {
            groupedNames != null -> groupedNames
            lastName != null && alias != null -> setOf("$lastName as $alias")
            lastName != null -> setOf(lastName)
            else -> null
        }
    }

private val RsUseItem.pathAsList: List<String>?
    get() = useSpeck?.path?.text?.split("::")

@Suppress("DataClassPrivateConstructor")
data class ImportContext private constructor(
    val project: Project,
    val mod: RsMod,
    val superMods: LinkedHashSet<RsMod>,
    val scope: GlobalSearchScope,
    val pathParsingMode: PathParsingMode,
    val attributes: RsFile.Attributes,
    val namespaceFilter: (RsQualifiedNamedElement) -> Boolean
) {
    companion object {
        fun from(project: Project, path: RsPath, isCompletion: Boolean): ImportContext = ImportContext(
            project = project,
            mod = path.containingMod,
            superMods = LinkedHashSet(path.containingMod.superMods),
            scope = RsWithMacrosProjectScope(project),
            pathParsingMode = path.pathParsingMode,
            attributes = path.stdlibAttributes,
            namespaceFilter = path.namespaceFilter(isCompletion)
        )

        fun from(project: Project, element: RsElement): ImportContext = ImportContext(
            project = project,
            mod = element.containingMod,
            superMods = LinkedHashSet(element.containingMod.superMods),
            scope = RsWithMacrosProjectScope(project),
            pathParsingMode = PathParsingMode.TYPE,
            attributes = element.stdlibAttributes,
            namespaceFilter = { true }
        )
    }
}

private val RsPath.pathParsingMode: PathParsingMode
    get() = when (parent) {
        is RsPathExpr,
        is RsStructLiteral,
        is RsPatStruct,
        is RsPatTupleStruct -> PathParsingMode.VALUE
        else -> PathParsingMode.TYPE
    }
private val RsElement.stdlibAttributes: RsFile.Attributes
    get() = (crateRoot?.containingFile as? RsFile)?.attributes ?: RsFile.Attributes.NONE
private val RsItemsOwner.firstItem: RsElement get() = itemsAndMacros.first { it !is RsAttr && it !is RsVis }
private val <T : RsElement> List<T>.lastElement: T? get() = maxBy { it.textOffset }

private val CargoWorkspace.Target.isStd: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.STD

private val CargoWorkspace.Target.isCore: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.CORE

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
