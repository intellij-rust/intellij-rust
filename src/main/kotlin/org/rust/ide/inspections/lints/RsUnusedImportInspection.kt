/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.NamedItem
import org.rust.lang.core.resolve2.exportedItems
import org.rust.lang.core.resolve2.isNewResolveEnabled

class RsUnusedImportInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedImports

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitUseSpeck(o: RsUseSpeck) {
            if (!holder.project.isNewResolveEnabled) return
            val item = o.ancestorStrict<RsUseItem>() ?: return
            if (item.isReexport) return
            if (o.path?.resolveStatus != PathResolveStatus.RESOLVED) return

            // Do not check uses if there is a child mod inside the current mod
            val parentMod = o.containingMod
            val hasChildMods = parentMod.expandedItemsExceptImplsAndUses.any {
                it is RsModItem || it is RsModDeclItem
            }
            if (o.parentOfType<RsFunction>() == null && hasChildMods) return

            checkUseSpeck(o, holder)
        }
    }

    private fun checkUseSpeck(useSpeck: RsUseSpeck, holder: RsProblemsHolder) {
        if (!useSpeck.isUsed()) {
            val element = getHighlightElement(useSpeck)
            holder.registerLintProblem(
                element,
                "Unused import: `${useSpeck.text}`"
            )
        }
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
private fun RsUseSpeck.isUsed(): Boolean {
    val owner = this.parentOfType<RsUseItem>()?.parent as? RsItemsOwner ?: return true
    val usage = owner.pathUsage
    return isUseSpeckUsed(this, usage)
}

private fun isUseSpeckUsed(useSpeck: RsUseSpeck, usage: PathUsageMap): Boolean {
    // Use speck with an empty group is always unused
    val useGroup = useSpeck.useGroup
    if (useGroup != null && useGroup.useSpeckList.isEmpty()) return false

    val items = if (useSpeck.isStarImport) {
        val module = useSpeck.path?.reference?.resolve() as? RsMod ?: return true
        module.exportedItems(useSpeck.containingMod)
    } else {
        val path = useSpeck.path ?: return true
        val items = path.reference?.multiResolve() ?: return true
        val name = useSpeck.itemName(withAlias = true) ?: return true
        items.filterIsInstance<RsNamedElement>().map { NamedItem(name, it) }
    }

    // TODO: remove after macros are supported in RsPathUsageAnalysis.kt
    if (items.any { (_, item) -> item is RsMacro }) {
        return true
    }

    return items.any { (name, item) ->
        item in usage.pathUsages[name].orEmpty()
            || item in usage.traitUsages
    }
}
