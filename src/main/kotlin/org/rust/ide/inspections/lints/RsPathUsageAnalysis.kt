/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.inference
import org.rust.openapiext.processElementsWithMacros

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

    for (child in owner.children) {
        processElementsWithMacros(child) { element ->
            handleElement(element, directUsages, traitUsages)
        }
    }

    return PathUsageMap(directUsages, traitUsages)
}

private fun handleElement(
    element: PsiElement,
    directUsages: MutableMap<String, MutableSet<RsElement>>,
    traitUsages: MutableSet<RsTraitItem>
): Boolean {
    fun addItem(name: String, item: RsElement) {
        directUsages.getOrPut(name) { mutableSetOf() }.add(item)
    }

    if (!element.isEnabledByCfg) return false

    return when (element) {
        is RsModItem -> false
        is RsUseItem -> {
            val useSpeck = element.useSpeck ?: return false

            if (useSpeck.alias == null) return false
            val path = useSpeck.path ?: return false

            if (path.qualifier != null) return false
            val item = path.reference?.resolve() ?: return false
            val name = path.referenceName ?: return false

            addItem(name, item)
            false
        }
        is RsPath -> {
            val base = element.basePath()

            if (base == element) {
                val name = element.referenceName ?: return false
                val targets = element.reference?.multiResolve().orEmpty()
                targets.forEach {
                    addItem(name, it)
                }
            } else {
                val requiredTraits = getAssociatedItemRequiredTraits(element).orEmpty()
                traitUsages.addAll(requiredTraits)
            }
            true
        }
        is RsMethodCall -> {
            val requiredTraits = getMethodRequiredTraits(element).orEmpty()
            traitUsages.addAll(requiredTraits)
            true
        }
        else -> true
    }
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
