/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.fixes.RemoveImportFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.NamedItem
import org.rust.lang.core.resolve2.exportedItems
import org.rust.lang.core.resolve2.isNewResolveEnabled

class RsUnusedImportInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedImports

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitUseItem(item: RsUseItem) {
            if (!holder.project.isNewResolveEnabled) return
            if (item.isReexport) return

            // Do not check uses if there is a child mod inside the current mod
            val parentMod = item.containingMod
            val hasChildMods = parentMod.expandedItemsExceptImplsAndUses.any {
                it is RsModItem || it is RsModDeclItem
            }
            if (item.parentOfType<RsFunction>() == null && hasChildMods) return

            val owner = item.parent as? RsItemsOwner ?: return
            val usage = owner.pathUsage

            val speck = item.useSpeck ?: return

            val isSpeckUsedMap = HashMap<RsUseSpeck, Boolean>()
            storeUseSpeckUsage(speck, usage, isSpeckUsedMap)
            markUnusedSpecks(speck, isSpeckUsedMap, holder)
        }
    }

    private fun markUnusedSpecks(
        useSpeck: RsUseSpeck,
        isSpeckUsedMap: Map<RsUseSpeck, Boolean>,
        holder: RsProblemsHolder
    ) {
        val used = isSpeckUsedMap[useSpeck] ?: return

        val group = useSpeck.useGroup
        if (group == null) {
            if (!used) {
                markAsUnused(useSpeck, holder)
            }
        } else {
            if (group.useSpeckList.none { isSpeckUsedMap[it] == true }) {
                markAsUnused(useSpeck, holder)
            } else {
                group.useSpeckList.forEach {
                    markUnusedSpecks(it, isSpeckUsedMap, holder)
                }
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
}

/**
 * Traverse all use specks recursively and store information about their usage.
 */
private fun storeUseSpeckUsage(
    useSpeck: RsUseSpeck,
    usage: PathUsageMap,
    isSpeckUsedMap: MutableMap<RsUseSpeck, Boolean>
) {
    val group = useSpeck.useGroup
    val isUsed = if (group == null) {
        isUseSpeckUsed(useSpeck, usage)
    } else {
        group.useSpeckList.forEach {
            storeUseSpeckUsage(it, usage, isSpeckUsedMap)
        }
        group.useSpeckList.any { isSpeckUsedMap[it] == true }
    }
    isSpeckUsedMap[useSpeck] = isUsed
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
        item in usage.pathUsages[name].orEmpty()
            || item in usage.traitUsages
    }
}
