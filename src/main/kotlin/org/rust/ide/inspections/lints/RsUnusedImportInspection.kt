/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.fixes.RemoveImportFix
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.NamedItem
import org.rust.lang.core.resolve2.exportedItems
import org.rust.lang.core.resolve2.isNewResolveEnabled

class RsUnusedImportInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedImports

    override fun getShortName(): String = SHORT_NAME

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitUseItem(item: RsUseItem) {
            if (!isApplicableForUseItem(item)) return

            // It's common to include more imports than needed in doctest sample code
            if (item.containingCrate is DoctestCrate) return

            val owner = item.parent as? RsItemsOwner ?: return
            val usage = owner.pathUsage

            val speck = item.useSpeck ?: return

            val unusedSpeckSet = HashSet<RsUseSpeck>()
            storeUseSpeckUsage(speck, usage, unusedSpeckSet)
            markUnusedSpecks(speck, unusedSpeckSet, holder)
        }
    }

    /**
     * Traverse all use specks recursively and store information about their usage.
     */
    private fun storeUseSpeckUsage(
        useSpeck: RsUseSpeck,
        usage: PathUsageMap,
        unusedSpeckSet: MutableSet<RsUseSpeck>
    ) {
        val group = useSpeck.useGroup
        val isUsed = if (group == null) {
            isUseSpeckUsed(useSpeck, usage)
        } else {
            group.useSpeckList.forEach {
                storeUseSpeckUsage(it, usage, unusedSpeckSet)
            }
            group.useSpeckList.any { it !in unusedSpeckSet }
        }
        if (!isUsed) {
            unusedSpeckSet.add(useSpeck)
        }
    }

    private fun markUnusedSpecks(
        useSpeck: RsUseSpeck,
        unusedSpeckSet: Set<RsUseSpeck>,
        holder: RsProblemsHolder
    ) {
        val used = useSpeck !in unusedSpeckSet

        if (!used) {
            markAsUnused(useSpeck, holder)
        } else {
            useSpeck.useGroup?.useSpeckList?.forEach {
                markUnusedSpecks(it, unusedSpeckSet, holder)
            }
        }
    }

    private fun markAsUnused(useSpeck: RsUseSpeck, holder: RsProblemsHolder) {
        val element = getHighlightElement(useSpeck)
        holder.registerLintProblem(
            element,
            "Unused import: `${useSpeck.text}`",
            RemoveImportFix(element)
        )
    }

    companion object {
        fun isApplicableForUseItem(item: RsUseItem): Boolean {
            if (!item.project.isNewResolveEnabled) return false
            if (item.isReexport) return false
            if (!item.isEnabledByCfg) return false

            // Do not check uses if there is a child mod inside the current mod
            val parentMod = item.containingMod
            val hasChildMods = parentMod.expandedItemsExceptImplsAndUses.any {
                it is RsModItem || it is RsModDeclItem
            }
            if (item.parentOfType<RsFunction>() == null && hasChildMods) return false
            return true
        }

        fun isEnabled(project: Project): Boolean {
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
            return profile.isToolEnabled(HighlightDisplayKey.find(SHORT_NAME))
        }

        private const val SHORT_NAME: String = "RsUnusedImport"
    }
}

private fun getHighlightElement(useSpeck: RsUseSpeck): PsiElement {
    if (useSpeck.parent is RsUseItem) {
        return useSpeck.parent
    }
    return useSpeck
}

/**
 * Returns true if this use speck has at least one usage in its containing owner (module or function).
 * A usage can be either a path that uses the import of the use speck or a method call/associated item available through
 * a trait that is imported by this use speck.
 */
fun RsUseSpeck.isUsed(): Boolean {
    val owner = this.parentOfType<RsUseItem>()?.parent as? RsItemsOwner ?: return true
    val usage = owner.pathUsage
    return isUseSpeckUsed(this, usage)
}

private fun isUseSpeckUsed(useSpeck: RsUseSpeck, usage: PathUsageMap): Boolean {
    if (useSpeck.path?.resolveStatus != PathResolveStatus.RESOLVED) return true

    val items = if (useSpeck.isStarImport) {
        val module = useSpeck.path?.reference?.resolve() as? RsMod ?: return true
        module.exportedItems(useSpeck.containingMod)
    } else {
        val path = useSpeck.path ?: return true
        val items = path.reference?.multiResolve() ?: return true
        val name = useSpeck.itemName(withAlias = true) ?: return true
        items.filterIsInstance<RsNamedElement>().map { NamedItem(name, it) }
    }

    return items.any { (name, item) ->
        val used = item in usage.pathUsages[name].orEmpty()
            || item in usage.traitUsages
        val probablyUsed = name in usage.unresolvedPaths
            || (item as? RsTraitItem)?.expandedMembers.orEmpty().any { it.name in usage.unresolvedMethods }
        used || probablyUsed
    }
}
