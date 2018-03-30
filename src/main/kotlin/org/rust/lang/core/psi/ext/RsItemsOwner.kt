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
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT

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
    for (psi in itemsAndMacros) {
        when (psi) {
            is RsMacroCall ->
                if (processMacroCall(psi, processor, 0)) return true

            is RsItemElement ->
                if (processor(psi)) return true
        }
    }

    return false
}

fun processMacroCall(call: RsMacroCall, processor: (RsItemElement) -> Boolean, recursionDepth: Int): Boolean {
    if (recursionDepth > DEFAULT_RECURSION_LIMIT) return true
    for (expanded in call.expansion.orEmpty()) {
        when (expanded) {
            is RsItemElement -> if (processor(expanded)) return true
            is RsMacroCall -> processMacroCall(expanded, processor, recursionDepth + 1)
        }
    }

    return false
}
