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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.search.RsCargoProjectScope
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TraitImplSource
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.canBeImported
import org.rust.lang.core.types.importItem
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.openapiext.Testmark
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
            is RsMethodCall -> findApplicableContext(project, element)  ?: return
            else -> return
        }

        if (candidates.size == 1) {
            project.runWriteCommandAction {
                importItem(project, candidates.first(), element.containingMod)
            }
        } else {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
                chooseItemAndImport(project, it, candidates, element.containingMod)
            }
        }
        isConsumed = true
    }

    private fun chooseItemAndImport(
        project: Project,
        dataContext: DataContext,
        items: List<ImportCandidate>,
        originalElement: RsElement
    ) {
        showItemsToImportChooser(project, dataContext, items) { selectedValue ->
            project.runWriteCommandAction {
                importItem(project, selectedValue, originalElement.containingMod)
            }
        }
    }

    companion object {

        const val NAME = "Import"

        fun findApplicableContext(project: Project, path: RsPath): Context<RsPath>? {
            val basePath = path.basePath()
            if (TyPrimitive.fromPath(basePath) != null) return null
            if (basePath.reference.multiResolve().isNotEmpty()) return null

            // Don't try to import path in use item
            if (path.ancestorStrict<RsUseSpeck>() != null) {
                Testmarks.pathInUseItem.hit()
                return Context(basePath, emptyList())
            }

            val pathMod = path.containingMod
            val pathSuperMods = LinkedHashSet(pathMod.superMods)

            val scope = RsCargoProjectScope(project.cargoProjects, GlobalSearchScope.allScope(project))

            val explicitItems = RsNamedElementIndex.findElementsByName(project, basePath.referenceName, scope)
                .asSequence()
                .filterIsInstance<RsQualifiedNamedElement>()
                .map { QualifiedNamedItem.ExplicitItem(it) }

            val reexportedItems = RsReexportIndex.findReexportsByName(project, basePath.referenceName, scope)
                .asSequence()
                .mapNotNull {
                    val item = it.path?.reference?.resolve() as? RsQualifiedNamedElement ?: return@mapNotNull null
                    QualifiedNamedItem.ReexportedItem(it, item)
                }

            val namespaceFilter = path.namespaceFilter
            val attributes = path.stdlibAttributes

            val candidates = (explicitItems + reexportedItems)
                .filter { basePath != path || !(it.item is RsMod || it.item is RsModDeclItem || it.item.parent is RsMembers) }
                .flatMap { it.withModuleReexports(project).asSequence() }
                .mapNotNull { importItem -> importItem.toImportCandidate(pathSuperMods) }
                // check that result after import can be resolved and resolved element is suitable
                // if no, don't add it in candidate list
                .filter { path.canBeResolvedToSuitableItem(project, pathMod, it.info, attributes, namespaceFilter) }
                .filterImportCandidates(attributes)

            return Context(basePath, candidates)
        }

        fun findApplicableContext(project: Project, methodCall: RsMethodCall): Context<Unit>? {
            val results = methodCall.inference?.getResolvedMethod(methodCall) ?: emptyList()
            if (results.isEmpty()) return Context(Unit, emptyList())

            val traitsToImport = collectTraitsToImport(methodCall, results) ?: return null

            val superMods = LinkedHashSet(methodCall.containingMod.superMods)
            val attributes = methodCall.stdlibAttributes

            val candidates = traitsToImport.asSequence()
                .flatMap { QualifiedNamedItem.ExplicitItem(it).withModuleReexports(project).asSequence() }
                .mapNotNull { importItem -> importItem.toImportCandidate(superMods) }
                .filterImportCandidates(attributes)

            return Context(Unit, candidates)
        }

        private fun QualifiedNamedItem.toImportCandidate(superMods: LinkedHashSet<RsMod>): ImportCandidate? =
            canBeImported(superMods)?.let { ImportCandidate(this, it) }

        private fun collectTraitsToImport(
            methodCall: RsMethodCall,
            resolveResults: List<MethodResolveVariant>
        ): List<RsTraitItem>? {
            val traits = resolveResults.mapNotNull { variant ->
                val source = variant.source
                val trait = when (source) {
                    is TraitImplSource.ExplicitImpl -> {
                        val impl = source.value
                        if (impl.traitRef == null) return null
                        impl.traitRef?.resolveToTrait ?: return@mapNotNull null
                    }
                    is TraitImplSource.Derived -> source.value
                    is TraitImplSource.Collapsed -> source.value
                    is TraitImplSource.Hardcoded -> source.value

                    is TraitImplSource.TraitBound -> return null
                    is TraitImplSource.Object -> return null
                }

                trait
            }
            return if (traits.filterInScope(methodCall).isNotEmpty()) null else traits
        }

        private fun RsPath.canBeResolvedToSuitableItem(
            project: Project,
            context: RsMod,
            info: ImportInfo,
            attributes: RsFile.Attributes,
            namespaceFilter: (RsQualifiedNamedElement) -> Boolean
        ): Boolean {
            val externCrateName = if (info !is ImportInfo.ExternCrateImportInfo ||
                attributes == RsFile.Attributes.NONE && info.target.isStd ||
                attributes == RsFile.Attributes.NO_STD && info.target.isCore) {
                null
            } else {
                info.externCrateName
            }
            val path = RsCodeFragmentFactory(project)
                .createPathInTmpMod(context, this, info.usePath, externCrateName) ?: return false
            val element = path.reference.deepResolve() as? RsQualifiedNamedElement ?: return false
            if (!namespaceFilter(element)) return false
            return !(element.parent is RsMembers && element.ancestorStrict<RsTraitItem>() != null)
        }

        private fun Sequence<ImportCandidate>.filterImportCandidates(
            attributes: RsFile.Attributes
        ): List<ImportCandidate> = groupBy { it.qualifiedNamedItem.item }
            .flatMap { (_, candidates) -> filterForSingleItem(candidates, attributes) }

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
            val (simpleImports, compositeImports) = candidates.partition {
                it.qualifiedNamedItem !is QualifiedNamedItem.CompositeItem
            }

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
                val superMods = it.qualifiedNamedItem.superMods ?: return@mapNotNull null
                it to superMods
            }
            val parents = candidatesWithSuperMods.mapTo(HashSet()) { (_, superMods) -> superMods[0] }
            val filteredSimpleImports = candidatesWithSuperMods.mapNotNull { (candidate, superMods) ->
                if (superMods.asSequence().drop(1).none { it in parents }) candidate else null
            }
            return filteredSimpleImports + compositeImports
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
    }
}

sealed class ImportInfo {

    abstract val usePath: String

    class LocalImportInfo(override val usePath: String): ImportInfo()

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

private val RsPath.namespaceFilter: (RsQualifiedNamedElement) -> Boolean get() = when (context) {
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

private val RsElement.stdlibAttributes: RsFile.Attributes
    get() = (crateRoot?.containingFile as? RsFile)?.attributes ?: RsFile.Attributes.NONE
private val RsItemsOwner.firstItem: RsElement get() = itemsAndMacros.first { it !is RsAttr }
private val <T: RsElement> List<T>.lastElement: T? get() = maxBy { it.textOffset }

private val CargoWorkspace.Target.isStd: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.STD

private val CargoWorkspace.Target.isCore: Boolean
    get() = pkg.origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.CORE
