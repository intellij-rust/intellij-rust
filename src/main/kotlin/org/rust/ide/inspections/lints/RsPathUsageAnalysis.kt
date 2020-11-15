/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsItemsOwner
import org.rust.lang.core.psi.ext.basePath
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.inference

data class PathUsageMap(
    val pathUsages: Map<String, Set<RsElement>>,
    val traitUsages: Set<RsTraitItem>
)

private val PATH_USAGE_KEY: Key<CachedValue<PathUsageMap>> = Key.create("PATH_USAGE_KEY")

val RsItemsOwner.pathUsage: PathUsageMap
    get() = CachedValuesManager.getCachedValue(this, PATH_USAGE_KEY) {
        val usages = calculatePathUsages(this)
        CachedValueProvider.Result.create(usages, PsiModificationTracker.MODIFICATION_COUNT)
    }

private fun calculatePathUsages(owner: RsItemsOwner): PathUsageMap {
    val directUsages = mutableMapOf<String, MutableSet<RsElement>>()
    val traitUsages = mutableSetOf<RsTraitItem>()

    class PathVisitor : RsRecursiveVisitor() {
        override fun visitElement(element: RsElement) {
            if (element.isEnabledByCfg) {
                super.visitElement(element)
            }
        }

        override fun visitModItem(o: RsModItem) {
            // stop at module boundaries
        }

        override fun visitUseItem(o: RsUseItem) {
            // ignore usages in use items
        }

        override fun visitPath(path: RsPath) {
            val base = path.basePath()

            if (base == path) {
                val name = path.referenceName ?: return
                val targets = path.reference?.multiResolve().orEmpty()
                targets.forEach {
                    addItem(name, it)
                }
            } else {
                val requiredTraits = getAssociatedItemRequiredTraits(path).orEmpty()
                traitUsages.addAll(requiredTraits)
                super.visitPath(path)
            }
        }

        override fun visitMethodCall(o: RsMethodCall) {
            val requiredTraits = getMethodRequiredTraits(o).orEmpty()
            traitUsages.addAll(requiredTraits)
            super.visitMethodCall(o)
        }

        private fun addItem(name: String, item: RsElement) {
            directUsages.getOrPut(name) { mutableSetOf() }.add(item)
        }
    }

    val visitor = PathVisitor()
    owner.acceptChildren(visitor)

    return PathUsageMap(directUsages, traitUsages)
}

private fun getMethodRequiredTraits(call: RsMethodCall): Set<RsTraitItem>? {
    val result = call.inference?.getResolvedMethod(call) ?: return null
    return result.mapNotNull {
        it.source.implementedTrait?.element
    }.toSet()
}

private fun getAssociatedItemRequiredTraits(path: RsPath): Set<RsTraitItem>? {
    val parent = path.parent as? RsPathExpr ?: return null
    val resolved = path.inference?.getResolvedPath(parent) ?: return null
    return resolved.mapNotNull {
        if (it is ResolvedPath.AssocItem) {
            it.source.implementedTrait?.element
        } else null
    }.toSet()
}
