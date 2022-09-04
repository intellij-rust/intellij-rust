/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.Key
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.SmartList
import org.jetbrains.annotations.VisibleForTesting
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCachedItems.CachedNamedImport
import org.rust.lang.core.psi.ext.RsCachedItems.CachedStarImport
import org.rust.lang.core.resolve2.getRecursionLimit
import org.rust.lang.core.resolve2.util.SmartListMap
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.openapiext.testAssert
import org.rust.stdext.optimizeList
import org.rust.stdext.replaceTrivialMap
import kotlin.LazyThreadSafetyMode.PUBLICATION

interface RsItemsOwner : RsElement

val RsItemsOwner.itemsAndMacros: Sequence<RsElement>
    get() {
        val stubChildren = run {
            when (this) {
                is RsFile -> {
                    val stub = greenStub
                    if (stub != null) return@run stub.childrenStubs
                }
                is StubBasedPsiElement<*> -> {
                    val stub = this.greenStub
                    if (stub != null) return@run stub.childrenStubs
                }
            }
            null
        }

        @Suppress("IfThenToElvis")
        return if (stubChildren != null) {
            stubChildren.asSequence().map { it.psi }
        } else {
            generateSequence(firstChild) { it.nextSibling }
        }.filterIsInstance<RsElement>()
    }

inline fun RsItemsOwner.processExpandedItemsExceptImplsAndUses(processor: (RsItemElement) -> Boolean): Boolean {
    for (element in expandedItemsExceptImplsAndUses) {
        if (processor(element)) return true
    }
    return false
}

val RsItemsOwner.expandedItemsExceptImplsAndUses: List<RsItemElement>
    get() = expandedItemsCached.cfgEnabledNamedItems

private val EXPANDED_ITEMS_KEY: Key<CachedValue<RsCachedItems>> = Key.create("EXPANDED_ITEMS_KEY")

val RsItemsOwner.expandedItemsCached: RsCachedItems
    get() = CachedValuesManager.getCachedValue(this, EXPANDED_ITEMS_KEY) {
        val imports = SmartList<RsUseItem>()
        val macros = SmartList<RsMacro>()
        val named: SmartListMap<String, RsItemElement> = SmartListMap()
        processExpandedItemsInternal {
            when (it) {
                is RsUseItem -> imports.add(it)
                is RsMacro -> macros.add(it)
                is RsItemElement -> {
                    if (it is RsForeignModItem && it.isEnabledByCfgSelf()) {
                        for (item in it.stubChildrenOfType<RsItemElement>()) {
                            val name = item.name ?: continue
                            named.addValue(name, item)
                        }
                    } else {
                        val name = when (it) {
                            is RsExternCrateItem -> it.nameWithAlias
                            is RsFunction -> if (it.isProcMacroDef) it.procMacroName else it.name
                            else -> it.name
                        } ?: return@processExpandedItemsInternal false
                        named.addValue(name, it)
                    }
                }
            }
            false
        }
        // In the case of ambiguity, remove cfg-disabled items
        for (entry in named) {
            val value = entry.value
            if (value.size > 1) {
                val cfgEnabledList = value.filter { it.isEnabledByCfgSelf() }
                if (cfgEnabledList.isNotEmpty()) {
                    entry.setValue(cfgEnabledList)
                } else {
                    // All values are cfg-disabled, leave them as they are
                }
            }
        }
        val localModTracker = if (this is RsBlock) {
            findModificationTrackerOwner(strict = true)
        } else {
            null
        }
        CachedValueProvider.Result.create(
            RsCachedItems(
                lazy(PUBLICATION) { lowerImports(imports) },
                macros.optimizeList(),
                named.replaceTrivialMap(),
            ),
            listOfNotNull(rustStructureOrAnyPsiModificationTracker, localModTracker)
        )
    }

private fun lowerImports(imports: List<RsUseItem>): NamedAndStarImports {
    val namedImports = SmartList<CachedNamedImport>()
    val starImports = SmartList<CachedStarImport>()
    for (use in imports) {
        if (!use.isEnabledByCfgSelf()) continue

        val isPublic = use.isPublic
        use.useSpeck?.forEachLeafSpeck { speck ->
            if (speck.isStarImport) {
                starImports += CachedStarImport(isPublic, speck)
            } else {
                testAssert { speck.useGroup == null }
                val path = speck.path ?: return@forEachLeafSpeck
                val nameInScope = speck.nameInScope ?: return@forEachLeafSpeck
                val isAtom = speck.alias == null && path.isAtom
                namedImports += CachedNamedImport(isPublic, path, nameInScope, isAtom)
            }
        }
    }
    return NamedAndStarImports(
        namedImports.optimizeList(),
        starImports.optimizeList(),
    )
}

/**
 * Used for optimization purposes, to reduce access to a cache and PSI tree in one very hot
 * place - [org.rust.lang.core.resolve2.processItemDeclarations]
 */
class RsCachedItems(
    private val imports: Lazy<NamedAndStarImports>,
    /** May contain cfg-disabled items. [RsMacro2] are stored in [named] */
    val legacyMacros: List<RsMacro>,
    /** May contain cfg-disabled items*/
    val named: Map<String, List<RsItemElement>>,
) {
    val namedImports: List<CachedNamedImport> get() = imports.value.namedImports
    val starImports: List<CachedStarImport> get() = imports.value.starImports

    val cfgEnabledNamedItems: List<RsItemElement> by lazy(PUBLICATION) {
        named.values.asSequence()
            .flatten()
            .filter { it.isEnabledByCfgSelf() }
            .toList()
    }

    fun getNamedElementsIfCfgEnabled(name: String): List<RsItemElement>? =
        named[name]?.filter { it.isEnabledByCfgSelf() }?.takeIf { it.isNotEmpty() }

    data class CachedNamedImport(
        val isPublic: Boolean,
        val path: RsPath,
        val nameInScope: String,
        val isAtom: Boolean
    )

    data class CachedStarImport(val isPublic: Boolean, val speck: RsUseSpeck)
}

class NamedAndStarImports(
    val namedImports: List<CachedNamedImport>,
    val starImports: List<CachedStarImport>,
)

@VisibleForTesting
fun RsItemsOwner.processExpandedItemsInternal(
    withMacroCalls: Boolean = false,
    processor: (RsElement) -> Boolean
): Boolean {
    val recursionLimit = getRecursionLimit(this)
    return itemsAndMacros.any { it.processItem(withMacroCalls, recursionLimit, processor) }
}

private fun RsElement.processItem(
    withMacroCalls: Boolean,
    recursionLimit: Int,
    processor: (RsElement) -> Boolean
): Boolean {
    val derives: Sequence<RsMetaItem>? = if (this is RsAttrProcMacroOwner) {
        when (val attr = procMacroAttributeWithDerives) {
            is ProcMacroAttribute.Attr -> {
                if (withMacroCalls) {
                    if (processor(attr.attr)) return true
                }
                if (!isEnabledByCfgSelf()) return false
                return attr.attr.expansion?.elements.orEmpty().any {
                    it.processItem(withMacroCalls, recursionLimit, processor)
                }
            }
            is ProcMacroAttribute.Derive -> attr.derives
            ProcMacroAttribute.None -> null
        }
    } else {
        null
    }

    when (this) {
        is RsMacroCall -> {
            if (withMacroCalls) {
                if (processor(this)) return true
            }
            if (isEnabledByCfgSelf()) {
                processExpansionRecursively(recursionLimit) {
                    it.processItem(withMacroCalls, recursionLimit, processor)
                }
            }
        }
        is RsItemElement, is RsMacro -> {
            if (processor(this)) return true
        }
    }

    // Processing derives *after* `this` item itself
    derives?.forEachIndexed { i, derive ->
        if (i == 0 && !isEnabledByCfgSelf()) return false
        if (withMacroCalls) {
            if (processor(derive)) return true
        }
        val result = derive.expansion?.elements.orEmpty().any {
            it.processItem(withMacroCalls, recursionLimit, processor)
        }
        if (result) return true
    }

    return false
}

private fun RsElement.isEnabledByCfgSelf() =
    this !is RsDocAndAttributeOwner || evaluateCfg() != ThreeValuedLogic.False

private val RsPath.isAtom: Boolean
    get() = when (kind) {
        PathKind.IDENTIFIER -> qualifier == null
        PathKind.SELF -> qualifier?.isAtom == true
        else -> false
    }
