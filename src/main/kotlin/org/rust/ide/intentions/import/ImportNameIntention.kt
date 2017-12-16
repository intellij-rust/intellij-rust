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
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsNamedElementIndex
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
        // TODO: support reexports
        val candidates = RsNamedElementIndex.findElementsByName(project, basePath.referenceName)
            .filterIsInstance<RsQualifiedNamedElement>()
            .mapNotNull {
                val info = (it as? RsVisible)?.canBeImported(pathSuperMods) ?: return@mapNotNull null
                ImportCandidate(it, info)
            }
        if (candidates.isEmpty()) return null
        return Context(path, candidates)
    }

    // Semantic signature of method is `RsVisible.canBeImported(mod: RsMod)`
    // but in our case `mod` is always same and `mod` needs only to get set of its super mods
    // so we pass `superMods` instead of `mod` for optimization
    private fun RsVisible.canBeImported(superMods: Set<RsMod>): ImportInfo? {
        val parentMod = (if (this is RsMod) `super` else containingMod) ?: return null
        val ourSuperMods = parentMod.superMods

        // try to find latest common ancestor module of `parentMod` and `mod` in module tree
        // we need to do it because we can use direct child items of any super mod with any visibility
        val lca = ourSuperMods.find { it in superMods }

        val (shouldBePublicMods, importInfo) = if (lca == null) {
            if (!isPublic) return null
            val usePath = (this as? RsQualifiedNamedElement)?.qualifiedName ?: return null
            val target = containingCargoTarget ?: return null
            ourSuperMods to ImportInfo.ExternCrateImportInfo(usePath, ourSuperMods.last(), target)
        } else {
            val usePath = (this as? RsQualifiedNamedElement)?.crateRelativePath?.removePrefix("::") ?: return null
            // if current item is direct child of some ancestor of `mod` then it can be not public
            if (parentMod == lca) return ImportInfo.LocalImportInfo(usePath)
            if (!isPublic) return null
            ourSuperMods.takeWhile { it != lca }.dropLast(1) to ImportInfo.LocalImportInfo(usePath)
        }
        return if (shouldBePublicMods.all { it.isPublic }) return importInfo else null
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
            val (_, crateMod, target) = candidate.info
            val crateName = target.normName
            if (target.pkg.origin == PackageOrigin.STDLIB && crateName == AutoInjectedCrates.std) {
                // but if crate of imported element is `std`
                // we don't add corresponding extern crate item manually
                // because it will be done by compiler implicitly
                Testmarks.autoInjectedCrate.hit()
            } else {
                val needAddExternCrateItem = externCrateItems.none { it.reference.resolve() == crateMod }
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

sealed class ImportInfo {

    abstract val usePath: String

    data class LocalImportInfo(override val usePath: String): ImportInfo()

    data class ExternCrateImportInfo(
        override val usePath: String,
        val externCrateMod: RsMod,
        val target: CargoWorkspace.Target
    ) : ImportInfo()
}

data class ImportCandidate(val item: RsQualifiedNamedElement, val info: ImportInfo)

private fun RsItemsOwner.firstItem(): RsElement = itemsAndMacros.first()

private tailrec fun getBasePath(path: RsPath): RsPath {
    val qualifier = path.path
    return if (qualifier == null) path else getBasePath(qualifier)
}
