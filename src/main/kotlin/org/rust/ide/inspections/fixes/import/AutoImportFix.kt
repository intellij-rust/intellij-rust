/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes.import

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.openapiext.Testmark
import org.rust.openapiext.runWriteCommandAction

class AutoImportFix(path: RsPath) : LocalQuickFixOnPsiElement(path), HighPriorityAction {

    private var isConsumed: Boolean = false

    override fun getFamilyName(): String = NAME
    override fun getText(): String = familyName

    public override fun isAvailable(): Boolean = super.isAvailable() && !isConsumed

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        invoke(project)
    }

    fun invoke(project: Project) {
        val path = startElement as? RsPath ?: return
        val (_, candidates) = findApplicableContext(project, path) ?: return

        if (candidates.size == 1) {
            importItem(project, candidates.first(), path)
        } else {
            val consumer = Consumer<DataContext> { chooseItemAndImport(project, it, candidates, path) }
            DataManager.getInstance().dataContextFromFocus.doWhenDone(consumer)
        }
        isConsumed = true
    }

    private fun chooseItemAndImport(project: Project, dataContext: DataContext, items: List<ImportCandidate>, originalPath: RsPath) {
        showItemsToImportChooser(project, dataContext, items) { selectedValue ->
            importItem(project, selectedValue, originalPath)
        }
    }

    private fun importItem(project: Project, candidate: ImportCandidate, originalPath: RsPath): Unit = project.runWriteCommandAction {
        val mod = originalPath.containingMod

        val externCrateItems = mod.childrenOfType<RsExternCrateItem>()
        var lastExternCrateItem = externCrateItems.maxBy { it.textOffset }

        val psiFactory = RsPsiFactory(project)

        // if crate of importing element differs from current crate
        // we need to add new extern crate item
        if (candidate.info is ImportInfo.ExternCrateImportInfo) {
            val target = candidate.info.target
            val crateName = target.normName
            val attributes = originalPath.stdlibAttributes
            when {
                // but if crate of imported element is `std` and there aren't `#![no_std]` and `#![no_core]`
                // we don't add corresponding extern crate item manually
                // because it will be done by compiler implicitly
                attributes == RsFile.Attributes.NONE && target.isStd -> Testmarks.autoInjectedStdCrate.hit()
                // if crate of imported element is `core` and there is `#![no_std]`
                // we don't add corresponding extern crate item manually for the same reason
                attributes == RsFile.Attributes.NO_STD && target.isCore -> Testmarks.autoInjectedCoreCrate.hit()
                else -> {
                    val needAddExternCrateItem = externCrateItems.none { it.reference.resolve() == candidate.info.externCrateMod }
                    if (needAddExternCrateItem) {
                        val externCrateItem = psiFactory.createExternCrateItem(crateName)
                        lastExternCrateItem = if (lastExternCrateItem != null) {
                            mod.addAfter(externCrateItem, lastExternCrateItem)
                        } else {
                            val insertedItem = mod.addBefore(externCrateItem, mod.firstItem)
                            mod.addAfter(psiFactory.createNewline(), mod.firstItem)
                            insertedItem
                        } as RsExternCrateItem?
                    }
                }
            }
        }

        val lastUseItem = mod.childrenOfType<RsUseItem>().maxBy { it.textOffset }
        val useItem = psiFactory.createUseItem(candidate.info.usePath)
        val anchor = lastUseItem ?: lastExternCrateItem

        if (anchor != null) {
            val insertedUseItem = mod.addAfter(useItem, anchor)
            if (anchor == lastExternCrateItem) {
                mod.addBefore(psiFactory.createNewline(), insertedUseItem)
            }
        } else {
            mod.addBefore(useItem, mod.firstItem)
            mod.addAfter(psiFactory.createNewline(), mod.firstItem)
        }
    }

    companion object {

        const val NAME = "Import"

        fun findApplicableContext(project: Project, path: RsPath): Context? {
            if (TyPrimitive.fromPath(path) != null) return null
            val basePath = getBasePath(path)
            if (basePath.reference.resolve() != null) return null
            val pathMod = path.containingMod
            val pathSuperMods = HashSet(pathMod.superMods)

            val explicitItems = RsNamedElementIndex.findElementsByName(project, basePath.referenceName)
                .asSequence()
                .filterIsInstance<RsQualifiedNamedElement>()
                .map { ImportItem.ExplicitItem(it) }

            val reexportedItems = RsReexportIndex.findReexportsByName(project, basePath.referenceName)
                .asSequence()
                .mapNotNull {
                    val item = it.path?.reference?.resolve() as? RsQualifiedNamedElement ?: return@mapNotNull null
                    ImportItem.ReexportedItem(it, item)
                }

            val attributes = (path.containingFile as? RsFile)?.attributes ?: RsFile.Attributes.NONE

            val candidates = (explicitItems + reexportedItems)
                .filter { basePath != path || !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers) }
                .flatMap { it.withModuleReexports(project).asSequence() }
                .mapNotNull { importItem -> importItem.canBeImported(pathSuperMods)?.let { ImportCandidate(importItem, it) } }
                // check that result after import can be resolved and resolved element is suitable
                // if no, don't add it in candidate list
                .filter { path.canBeResolvedToSuitableItem(project, pathMod, it.info, attributes) }
                .groupBy { it.importItem.item }
                .flatMap { (_, candidates) -> filterForSingleItem(candidates, attributes) }

            return Context(basePath, candidates)
        }

        /**
         * Collect all possible imports using reexports of modules from original import item path
         */
        private fun ImportItem.withModuleReexports(project: Project): List<ImportItem> {
            check(this is ImportItem.ExplicitItem || this is ImportItem.ReexportedItem) {
                "`ImportItem.withModuleReexports` should be called only for `ImportItem.ExplicitItem` and `ImportItem.ReexportedItem`"
            }

            // Contains already visited edges of module graph
            // (useSpeck element <-> reexport edge of module graph).
            // Only reexports can create cycles in module graph
            // so it's enough to collect only such edges
            val visited: MutableSet<RsUseSpeck> = HashSet()

            fun ImportItem.collectImportItems(): List<ImportItem> {
                val importItems = mutableListOf(this)
                val superMods = superMods.orEmpty()
                superMods
                    // only public items can be reexported
                    .filter { it.isPublic }
                    .forEachIndexed { index, ancestorMod ->
                        val modName = ancestorMod.modName ?: return@forEachIndexed
                        RsReexportIndex.findReexportsByName(project, modName)
                            .mapNotNull {
                                if (it in visited) return@mapNotNull null
                                val reexportedMod = it.path?.reference?.resolve() as? RsMod
                                if (reexportedMod != ancestorMod) return@mapNotNull null
                                it to reexportedMod
                            }
                            .forEach { (useSpeck, reexportedMod) ->
                                visited += useSpeck
                                val items = ImportItem.ReexportedItem(useSpeck, reexportedMod).collectImportItems()
                                importItems += items.map {
                                    ImportItem.CompositeImportItem(itemName, isPublic, it, superMods.subList(0, index + 1), item)
                                }
                                visited -= useSpeck
                            }
                    }
                return importItems
            }

            return collectImportItems()
        }

        // Semantic signature of method is `ImportItem.canBeImported(mod: RsMod)`
        // but in our case `mod` is always same and `mod` needs only to get set of its super mods
        // so we pass `superMods` instead of `mod` for optimization
        private fun ImportItem.canBeImported(superMods: Set<RsMod>): ImportInfo? {
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
                ourSuperMods to ImportInfo.ExternCrateImportInfo(ourSuperMods.last(), target, crateRelativePath)
            } else {
                // if current item is direct child of some ancestor of `mod` then it can be not public
                if (parentMod == lca) return ImportInfo.LocalImportInfo(crateRelativePath)
                if (!isPublic) return null
                ourSuperMods.takeWhile { it != lca }.dropLast(1) to ImportInfo.LocalImportInfo(crateRelativePath)
            }
            return if (shouldBePublicMods.all { it.isPublic }) return importInfo else null
        }

        private fun RsPath.canBeResolvedToSuitableItem(
            project: Project,
            context: RsMod,
            info: ImportInfo,
            attributes: RsFile.Attributes
        ): Boolean {
            val externCrateName = if (info !is ImportInfo.ExternCrateImportInfo ||
                attributes == RsFile.Attributes.NONE && info.target.isStd ||
                attributes == RsFile.Attributes.NO_STD && info.target.isCore) {
                null
            } else {
                info.target.normName
            }
            val path = RsCodeFragmentFactory(project)
                .createPathInTmpMod(context, this, info.usePath, externCrateName) ?: return false
            val element = path.reference.resolve() ?: return false
            return !(element.parent is RsMembers && element.ancestorStrict<RsTraitItem>() != null)
        }

        private fun filterForSingleItem(
            candidates: List<ImportCandidate>,
            fileAttributes: RsFile.Attributes
        ): List<ImportCandidate> {
            val candidatesWithPackage = mutableListOf<Pair<ImportCandidate, CargoWorkspace.Package>>()

            val stdlibCandidates = mutableListOf<Pair<ImportCandidate, CargoWorkspace.Package>>()

            for (candidate in candidates) {
                val pkg = candidate.importItem.containingCargoTarget?.pkg ?: continue
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
                    AutoInjectedCrates.std -> RsFile.Attributes.NONE
                    AutoInjectedCrates.core -> RsFile.Attributes.NO_STD
                    else -> RsFile.Attributes.NO_CORE
                }
                hasImportWithSameAttributes = hasImportWithSameAttributes || attributes == fileAttributes
                candidate to attributes
            }

            val condition: (RsFile.Attributes) -> Boolean = if (hasImportWithSameAttributes) {
                attributes -> attributes == fileAttributes
            } else {
                attributes -> attributes < fileAttributes
            }
            return candidateToAttributes.mapNotNull { (candidate, attributes) ->
                if (condition(attributes)) candidate else null
            }
        }

        private fun filterInPackage(candidates: List<ImportCandidate>): List<ImportCandidate> {
            val (simpleImports, compositeImports) = candidates.partition { it.importItem !is ImportItem.CompositeImportItem }

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
            val candidatesWithSuperMods = simpleImports.mapNotNull {
                val superMods = it.importItem.superMods ?: return@mapNotNull null
                it to superMods
            }
            val parents = candidatesWithSuperMods.mapTo(HashSet()) { (_, superMods) -> superMods[0] }
            val filteredSimpleImports = candidatesWithSuperMods.mapNotNull { (candidate, superMods) ->
                if (superMods.asSequence().drop(1).none { it in parents }) candidate else null
            }
            return filteredSimpleImports + compositeImports
        }
    }

    data class Context(
        val basePath: RsPath,
        val candidates: List<ImportCandidate>
    )

    object Testmarks {
        val autoInjectedStdCrate = Testmark("autoInjectedStdCrate")
        val autoInjectedCoreCrate = Testmark("autoInjectedCoreCrate")
    }
}

sealed class ImportItem(val item: RsQualifiedNamedElement) {

    abstract val itemName: String?
    abstract val isPublic: Boolean
    abstract val superMods: List<RsMod>?
    abstract val containingCargoTarget: CargoWorkspace.Target?

    val parentCrateRelativePath: String? get() {
        val path = superMods
            ?.map { it.modName ?: return null }
            ?.asReversed()
            ?.drop(1)
            ?.joinToString("::") ?: return null
        return if (item is RsEnumVariant) item.parentEnum.name?.let { "$path::$it" } else path
    }

    val crateRelativePath: String? get() {
        val name = itemName ?: return null
        val parentPath = parentCrateRelativePath ?: return null
        if (parentPath.isEmpty()) return name
        return "$parentPath::$name"
    }

    class ExplicitItem(item: RsQualifiedNamedElement) : ImportItem(item) {
        override val itemName: String? get() = item.name
        override val isPublic: Boolean get() = (item as? RsVisible)?.isPublic == true
        override val superMods: List<RsMod>? get() = (if (item is RsMod) item.`super` else item.containingMod)?.superMods
        override val containingCargoTarget: CargoWorkspace.Target? get() = item.containingCargoTarget
    }

    class ReexportedItem(
        private val useSpeck: RsUseSpeck,
        item: RsQualifiedNamedElement
    ) : ImportItem(item) {

        override val itemName: String? get() = useSpeck.nameInScope
        override val isPublic: Boolean get() = true
        override val superMods: List<RsMod>? get() = useSpeck.containingMod.superMods
        override val containingCargoTarget: CargoWorkspace.Target? get() = useSpeck.containingCargoTarget
    }

    class CompositeImportItem(
        override val itemName: String?,
        override val isPublic: Boolean,
        private val reexportedModItem: ImportItem,
        private val explicitSuperMods: List<RsMod>,
        item: RsQualifiedNamedElement
    ) : ImportItem(item) {

        override val superMods: List<RsMod>? get() {
            val mods = ArrayList(explicitSuperMods)
            mods += reexportedModItem.superMods.orEmpty()
            return mods
        }
        override val containingCargoTarget: CargoWorkspace.Target? get() = reexportedModItem.containingCargoTarget
    }
}

sealed class ImportInfo {

    abstract val usePath: String

    class LocalImportInfo(override val usePath: String): ImportInfo()

    class ExternCrateImportInfo(
        val externCrateMod: RsMod,
        val target: CargoWorkspace.Target,
        crateRelativePath: String
    ) : ImportInfo() {
        override val usePath: String = "${target.normName}::$crateRelativePath"
    }
}

data class ImportCandidate(val importItem: ImportItem, val info: ImportInfo)

private val RsPath.stdlibAttributes: RsFile.Attributes get() = (containingFile as? RsFile)?.attributes ?: RsFile.Attributes.NONE
private val RsItemsOwner.firstItem: RsElement get() = itemsAndMacros.first { it !is RsInnerAttr }

private val CargoWorkspace.Target.isStd: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.std

private val CargoWorkspace.Target.isCore: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.core

private tailrec fun getBasePath(path: RsPath): RsPath {
    val qualifier = path.path
    return if (qualifier == null) path else getBasePath(qualifier)
}
