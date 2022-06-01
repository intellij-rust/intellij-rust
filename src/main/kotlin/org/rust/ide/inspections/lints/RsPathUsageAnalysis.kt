/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.inference
import org.rust.openapiext.TreeStatus
import org.rust.openapiext.processElementsWithMacros

interface PathUsageMap {
    val pathUsages: Map<String, Set<RsElement>>
    val unresolvedPaths: Set<String>

    val traitUsages: Set<RsTraitItem>
    val unresolvedMethods: Set<String>
}

class PathUsageMapMutable : PathUsageMap {
    override val pathUsages: MutableMap<String, MutableSet<RsElement>> = hashMapOf()
    override val unresolvedPaths: MutableSet<String> = hashSetOf()

    override val traitUsages: MutableSet<RsTraitItem> = hashSetOf()
    override val unresolvedMethods: MutableSet<String> = hashSetOf()

    fun recordPath(name: String, items: List<RsElement>) {
        if (items.isEmpty()) {
            unresolvedPaths += name
        } else {
            pathUsages.getOrPut(name) { hashSetOf() } += items
        }
    }

    fun recordMethod(methodName: String, traits: Set<RsTraitItem>) {
        if (traits.isEmpty()) {
            unresolvedMethods += methodName
        } else {
            traitUsages += traits
        }
    }
}

private val PATH_USAGE_KEY: Key<CachedValue<PathUsageMap>> = Key.create("PATH_USAGE_KEY")

val RsItemsOwner.pathUsage: PathUsageMap
    get() = CachedValuesManager.getCachedValue(this, PATH_USAGE_KEY) {
        val usages = calculatePathUsages(this)
        CachedValueProvider.Result.create(usages, PsiModificationTracker.MODIFICATION_COUNT)
    }

private fun calculatePathUsages(owner: RsItemsOwner): PathUsageMap {
    val usage = PathUsageMapMutable()

    val crate = owner.containingCrate ?: return usage
    if (!owner.existsAfterExpansion(crate)) return usage

    for (child in owner.children) {
        handleSubtree(child, usage, crate)
    }
    return usage
}

private fun handleSubtree(root: PsiElement, usage: PathUsageMapMutable, crate: Crate) {
    processElementsWithMacros(root) { element ->
        if (handleElement(element, usage, crate)) {
            TreeStatus.VISIT_CHILDREN
        } else {
            TreeStatus.SKIP_CHILDREN
        }
    }
}

private fun handleElement(element: PsiElement, usage: PathUsageMapMutable, crate: Crate): Boolean {
    if (element is RsDocAndAttributeOwner && !element.existsAfterExpansionSelf(crate)) return false

    return when (element) {
        is RsModItem -> false
        is RsPatIdent -> {
            val name = element.patBinding.referenceName
            val targets = element.patBinding.reference.multiResolve()
            // if `targets` is empty, there is no way to distinguish "unresolved reference" and "usual pat ident"
            if (targets.isNotEmpty()) {
                usage.recordPath(name, targets)
            }
            true
        }
        is RsPath -> {
            val name = element.referenceName ?: return true
            if (element.qualifier != null || element.typeQual != null) {
                val requiredTraits = getAssociatedItemRequiredTraits(element).orEmpty()
                usage.recordMethod(name, requiredTraits)
            } else {
                val useSpeck = element.parentOfType<RsUseSpeck>()
                if (useSpeck == null || useSpeck.isTopLevel) {
                    if (name in IGNORED_USE_PATHS) return true
                    val targets = element.reference?.multiResolve().orEmpty()
                    usage.recordPath(name, targets)
                }
            }
            true
        }
        is RsMacroCall -> {
            handleSubtree(element.path, usage, crate)
            true
        }
        is RsMethodCall -> {
            val requiredTraits = getMethodRequiredTraits(element).orEmpty()
            usage.recordMethod(element.referenceName, requiredTraits)
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
