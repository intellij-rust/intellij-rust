/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.Key
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.SmartList
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCachedItems.CachedNamedImport
import org.rust.lang.core.psi.ext.RsCachedItems.CachedStarImport
import org.rust.openapiext.testAssert
import org.rust.stdext.optimizeList

interface RsItemsOwner : RsElement

val RsItemsOwner.itemsAndMacros: Sequence<RsElement>
    get() {
        val stubChildren: List<StubElement<*>>? = run {
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
    get() = expandedItemsCached.rest

private val EXPANDED_ITEMS_KEY: Key<CachedValue<RsCachedItems>> = Key.create("EXPANDED_ITEMS_KEY")

val RsItemsOwner.expandedItemsCached: RsCachedItems
    get() = CachedValuesManager.getCachedValue(this, EXPANDED_ITEMS_KEY) {
        val namedImports = SmartList<CachedNamedImport>()
        val starImports = SmartList<CachedStarImport>()
        val rest = SmartList<RsItemElement>()
        processExpandedItemsInternal {
            when (it) {
                // Optimization: impls are not named elements, so we don't need them for name resolution
                is RsImplItem -> Unit

                // Optimization: prepare use items to reduce PSI tree access in hotter code
                is RsUseItem ->  {
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

                else -> rest.add(it)
            }
            false
        }
        CachedValueProvider.Result.create(
            RsCachedItems(namedImports.optimizeList(), starImports.optimizeList(), rest.optimizeList()),
            rustStructureOrAnyPsiModificationTracker
        )
    }

/**
 * Used for optimization purposes, to reduce access to a cache and PSI tree in one very hot
 * place - [org.rust.lang.core.resolve.processItemDeclarations]
 */
class RsCachedItems(
    val namedImports: List<CachedNamedImport>,
    val starImports: List<CachedStarImport>,
    val rest: List<RsItemElement>
) {
    data class CachedNamedImport(
        val isPublic: Boolean,
        val path: RsPath,
        val nameInScope: String,
        val isAtom: Boolean
    )

    data class CachedStarImport(val isPublic: Boolean, val speck: RsUseSpeck)
}

private fun RsItemsOwner.processExpandedItemsInternal(processor: (RsItemElement) -> Boolean): Boolean {
    return itemsAndMacros.any { it.processItem(processor) }
}

private fun RsElement.processItem(processor: (RsItemElement) -> Boolean) = when (this) {
    is RsMacroCall -> processExpansionRecursively { it is RsItemElement && processor(it) }
    is RsItemElement -> processor(this)
    else -> false
}

private val RsPath.isAtom: Boolean
    get() = when (kind) {
        PathKind.IDENTIFIER -> qualifier == null
        PathKind.SELF -> qualifier?.isAtom == true
        else -> false
    }
