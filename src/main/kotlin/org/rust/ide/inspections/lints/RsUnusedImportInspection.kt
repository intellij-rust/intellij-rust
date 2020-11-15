/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.intersects

class RsUnusedImportInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedImports

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitUseSpeck(o: RsUseSpeck) {
            val item = o.ancestorStrict<RsUseItem>() ?: return
            if (item.isReexport) return

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

private fun RsUseSpeck.isUsed(): Boolean {
    val owner = this.parentOfType<RsUseItem>()?.parent as? RsItemsOwner ?: return true
    val usage = owner.pathUsage
    return isUseSpeckUsed(this, usage)
}

private fun isUseSpeckUsed(useSpeck: RsUseSpeck, usage: PathUsageMap): Boolean {
    val (candidateItems: Set<RsElement>, usedItems: Set<RsElement>) = if (useSpeck.isStarImport) {
        val module = useSpeck.path?.reference?.resolve() as? RsMod ?: return true

        // Extract all visible items from the target module
        val candidateItems = module.itemsAndMacros.filter {
            (it as? RsVisible)?.isVisibleFrom(useSpeck.containingMod) != null
        }

        // Find path usages with names of the extracted items
        val usedItems = candidateItems.mapNotNull {
            val name = (it as? RsNamedElement)?.name ?: return@mapNotNull null
            usage.pathUsages[name]
        }.fold(mutableSetOf<RsElement>()) { acc, value ->
            acc.addAll(value)
            acc
        }

        Pair(candidateItems.toSet(), usedItems)
    } else {
        val path = useSpeck.path ?: return true
        val items = path.reference?.multiResolve() ?: return true

        val name = useSpeck.itemName(withAlias = true) ?: return true
        val usedItems = usage.pathUsages[name].orEmpty()

        Pair(items.toSet(), usedItems)
    }

    return candidateItems.intersects(usedItems) || candidateItems.intersects(usage.traitUsages)
}
