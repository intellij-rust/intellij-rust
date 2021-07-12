/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsItemsOwner
import org.rust.lang.core.psi.ext.isEnabledByCfg
import org.rust.lang.core.psi.ext.qualifier
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
    val directUsages = hashMapOf<String, MutableSet<RsElement>>()
    val traitUsages = hashSetOf<RsTraitItem>()

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
        directUsages.getOrPut(name) { hashSetOf() }.add(item)
    }

    if (!element.isEnabledByCfg) return false

    return when (element) {
        is RsModItem -> false
        is RsPatIdent -> {
            val name = element.patBinding.referenceName
            val targets = element.patBinding.reference.multiResolve()
            targets.forEach {
                addItem(name, it)
            }
            true
        }
        is RsPath -> {
            if (element.qualifier != null) {
                val requiredTraits = getAssociatedItemRequiredTraits(element).orEmpty()
                traitUsages.addAll(requiredTraits)
            } else {
                val useSpeck = element.parentOfType<RsUseSpeck>()
                if (useSpeck == null || useSpeck.isTopLevel) {
                    val name = element.referenceName ?: return true
                    if (name in IGNORED_USE_PATHS) return true
                    val targets = element.reference?.multiResolve().orEmpty()
                    targets.forEach {
                        addItem(name, it)
                    }
                }
            }
            true
        }
        is RsMacroCall -> {
            processElementsWithMacros(element.path) {
                handleElement(it, directUsages, traitUsages)
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

private val IGNORED_USE_PATHS = listOf("crate", "self", "super")

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

/**
 * We should collect paths only from relative use specks,
 * that is top-level use specks without `::`
 * E.g. we shouldn't collect such paths: `use ::{foo, bar}`
 */
private val RsUseSpeck.isTopLevel: Boolean
    get() = (path != null || coloncolon == null)
        && parentOfType<RsUseSpeck>()?.isTopLevel != false
