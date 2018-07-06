/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.stubs.StubElement
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsModItem

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

fun RsItemsOwner.processExpandedItems(processor: (RsItemElement) -> Boolean): Boolean {
    return itemsAndMacros.any { it.processItem(processor) }
}

private fun RsElement.processItem(processor: (RsItemElement) -> Boolean) = when (this) {
    is RsMacroCall -> processExpansionRecursively { it is RsItemElement && processor(it) }
    is RsItemElement -> processor(this)
    else -> false
}
