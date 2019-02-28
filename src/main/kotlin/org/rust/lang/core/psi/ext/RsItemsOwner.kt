/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.Key
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.SmartList
import org.rust.lang.core.psi.*

interface RsItemsOwner : RsElement

val RsItemsOwner.itemsAndMacros: Sequence<RsElement>
    get() {
        val stubChildren: List<StubElement<*>>? = run {
            when (this) {
                is RsFile -> {
                    val stub = stub
                    if (stub != null) return@run stub.childrenStubs
                }
                is RsModItem -> {
                    val stub = stub
                    if (stub != null) return@run stub.childrenStubs
                }
                is RsBlock -> {
                    val stub = stub
                    if(stub != null) return@run stub.childrenStubs
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

inline fun RsItemsOwner.processExpandedItemsExceptImpls(processor: (RsItemElement) -> Boolean): Boolean {
    for (element in expandedItemsExceptImpls) {
        if (processor(element)) return true
    }
    return false
}

private val EXPANDED_ITEMS_KEY: Key<CachedValue<List<RsItemElement>>> = Key.create("EXPANDED_ITEMS_KEY")

val RsItemsOwner.expandedItemsExceptImpls: List<RsItemElement>
    get() = CachedValuesManager.getCachedValue(this, EXPANDED_ITEMS_KEY) {
        val items = SmartList<RsItemElement>()
        processExpandedItemsInternal {
            // optimization: impls are not named elements, so we don't need them for name resolution
            if (it !is RsImplItem) items.add(it)
            false
        }
        CachedValueProvider.Result.create(
            if (items.isNotEmpty()) items else emptyList(),
            rustStructureOrAnyPsiModificationTracker
        )
    }

private fun RsItemsOwner.processExpandedItemsInternal(processor: (RsItemElement) -> Boolean): Boolean {
    return itemsAndMacros.any { it.processItem(processor) }
}

private fun RsElement.processItem(processor: (RsItemElement) -> Boolean) = when (this) {
    is RsMacroCall -> processExpansionRecursively { it is RsItemElement && processor(it) }
    is RsItemElement -> processor(this)
    else -> false
}
