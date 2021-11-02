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
import org.rust.lang.core.resolve2.util.SmartListMap
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.openapiext.testAssert
import org.rust.stdext.optimizeList
import org.rust.stdext.replaceTrivialMap

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
    get() = expandedItemsCached.named.values.flatten()

private val EXPANDED_ITEMS_KEY: Key<CachedValue<RsCachedItems>> = Key.create("EXPANDED_ITEMS_KEY")

val RsItemsOwner.expandedItemsCached: RsCachedItems
    get() = CachedValuesManager.getCachedValue(this, EXPANDED_ITEMS_KEY) {
        val namedImports = SmartList<CachedNamedImport>()
        val starImports = SmartList<CachedStarImport>()
        val macros = SmartList<RsMacro>()
        val namedCfgEnabled: SmartListMap<String, RsItemElement> = SmartListMap()
        val namedCfgDisabled: SmartListMap<String, RsItemElement> = SmartListMap()
        processExpandedItemsInternal { it, isEnabledByCfgSelf ->
            when {
                // Optimization: impls are not named elements, so we don't need them for name resolution
                it is RsImplItem -> Unit

                // Optimization: prepare use items to reduce PSI tree access in hotter code
                isEnabledByCfgSelf && it is RsUseItem -> {
                    val isPublic = it.isPublic
                    it.useSpeck?.forEachLeafSpeck { speck ->
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

                isEnabledByCfgSelf && it is RsMacro -> macros.add(it)

                it is RsItemElement -> {
                    val named = if (isEnabledByCfgSelf) namedCfgEnabled else namedCfgDisabled
                    if (it is RsForeignModItem) {
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
        val localModTracker = if (this is RsBlock) {
            findModificationTrackerOwner(strict = true)
        } else {
            null
        }
        CachedValueProvider.Result.create(
            RsCachedItems(
                namedImports.optimizeList(),
                starImports.optimizeList(),
                macros.optimizeList(),
                namedCfgEnabled.replaceTrivialMap(),
                namedCfgDisabled.replaceTrivialMap()
            ),
            listOfNotNull(rustStructureOrAnyPsiModificationTracker, localModTracker)
        )
    }

/**
 * Used for optimization purposes, to reduce access to a cache and PSI tree in one very hot
 * place - [org.rust.lang.core.resolve.processItemDeclarations]
 */
class RsCachedItems(
    val namedImports: List<CachedNamedImport>,
    val starImports: List<CachedStarImport>,
    /** [RsMacro2] are stored in [named] */
    val legacyMacros: List<RsMacro>,
    val named: Map<String, List<RsItemElement>>,
    val namedCfgDisabled: Map<String, List<RsItemElement>>,
) {
    data class CachedNamedImport(
        val isPublic: Boolean,
        val path: RsPath,
        val nameInScope: String,
        val isAtom: Boolean
    )

    data class CachedStarImport(val isPublic: Boolean, val speck: RsUseSpeck)
}

@VisibleForTesting
fun RsItemsOwner.processExpandedItemsInternal(
    withMacroCalls: Boolean = false,
    processor: (RsElement, Boolean) -> Boolean
): Boolean {
    return itemsAndMacros.any { it.processItem(withMacroCalls, processor) }
}

private fun RsElement.processItem(withMacroCalls: Boolean, processor: (RsElement, Boolean) -> Boolean): Boolean {
    val existsAfterExpansionSelf = this !is RsDocAndAttributeOwner || evaluateCfg() != ThreeValuedLogic.False

    val derives: Sequence<RsMetaItem>? = if (this is RsAttrProcMacroOwner) {
        when (val attr = procMacroAttributeWithDerives) {
            is ProcMacroAttribute.Attr -> {
                if (withMacroCalls) {
                    if (processor(attr.attr, existsAfterExpansionSelf)) return true
                }
                if (!existsAfterExpansionSelf) return false
                return attr.attr.expansion?.elements.orEmpty().any {
                    it.processItem(withMacroCalls, processor)
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
                if (processor(this, existsAfterExpansionSelf)) return true
            }
            if (existsAfterExpansionSelf) {
                processExpansionRecursively {
                    it.processItem(withMacroCalls, processor)
                }
            }
        }
        is RsItemElement, is RsMacro -> {
            if (processor(this, existsAfterExpansionSelf)) return true
        }
    }

    // Processing derives *after* `this` item itself
    if (existsAfterExpansionSelf && derives != null) {
        for (derive in derives) {
            if (withMacroCalls) {
                if (processor(derive, existsAfterExpansionSelf)) return true
            }
            val result = derive.expansion?.elements.orEmpty().any {
                it.processItem(withMacroCalls, processor)
            }
            if (result) return true
        }
    }

    return false
}

private val RsPath.isAtom: Boolean
    get() = when (kind) {
        PathKind.IDENTIFIER -> qualifier == null
        PathKind.SELF -> qualifier?.isAtom == true
        else -> false
    }
