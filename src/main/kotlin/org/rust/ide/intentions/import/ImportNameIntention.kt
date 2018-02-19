/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.openapiext.Testmark
import org.rust.openapiext.runWriteCommandAction

class ImportNameIntention : RsElementBaseIntentionAction<ImportNameIntention.Context>(), HighPriorityAction {

    override fun getText() = "Import"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.ancestorStrict<RsPath>() ?: return null
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

        val candidates = (explicitItems + reexportedItems)
            .filter { basePath != path || !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers) }
            .flatMap { it.withModuleReexports(project).asSequence() }
            .mapNotNull { importItem -> importItem.canBeImported(pathSuperMods)?.let { ImportCandidate(importItem, it) } }
            // check that result after import can be resolved and resolved element is suitable
            // if no, don't add it in candidate list
            .filter { path.canBeResolvedToSuitableItem(project, pathMod, it.info) }
            .toList()

        if (candidates.isEmpty()) return null
        return Context(path, candidates)
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

    private fun RsPath.canBeResolvedToSuitableItem(project: Project, context: RsMod, info: ImportInfo): Boolean {
        val externCrateName = if (info is ImportInfo.ExternCrateImportInfo && !info.target.isStd) info.target.normName else null
        val path = RsCodeFragmentFactory(project)
            .createPathInTmpMod(context, this, info.usePath, externCrateName) ?: return false
        val element = path.reference.resolve() ?: return false
        return !(element.parent is RsMembers && element.ancestorStrict<RsTraitItem>() != null)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (path, candidates) = ctx

        if (candidates.size == 1) {
            importItem(project, candidates.first(), path)
        } else {
            chooseItemAndImport(project, editor, candidates, path)
        }
    }

    private fun chooseItemAndImport(project: Project, editor: Editor, items: List<ImportCandidate>, originalPath: RsPath) {
        showItemsToImportChooser(project, editor, items) { selectedValue ->
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
            if (target.isStd) {
                // but if crate of imported element is `std`
                // we don't add corresponding extern crate item manually
                // because it will be done by compiler implicitly
                Testmarks.autoInjectedCrate.hit()
            } else {
                val needAddExternCrateItem = externCrateItems.none { it.reference.resolve() == candidate.info.externCrateMod }
                if (needAddExternCrateItem) {
                    val externCrateItem = psiFactory.createExternCrateItem(crateName)
                    lastExternCrateItem = if (lastExternCrateItem != null) {
                        mod.addAfter(externCrateItem, lastExternCrateItem)
                    } else {
                        val insertedItem = mod.addBefore(externCrateItem, mod.firstItem())
                        mod.addAfter(psiFactory.createNewline(), mod.firstItem())
                        insertedItem
                    } as RsExternCrateItem?
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
            mod.addBefore(useItem, mod.firstItem())
            mod.addAfter(psiFactory.createNewline(), mod.firstItem())
        }
    }

    data class Context(
        val path: RsPath,
        val candidates: List<ImportCandidate>
    )

    object Testmarks {
        val autoInjectedCrate = Testmark("autoInjectedCrate")
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

private fun RsItemsOwner.firstItem(): RsElement = itemsAndMacros.first { it !is RsInnerAttr }

private val CargoWorkspace.Target.isStd: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.std

private tailrec fun getBasePath(path: RsPath): RsPath {
    val qualifier = path.path
    return if (qualifier == null) path else getBasePath(qualifier)
}
