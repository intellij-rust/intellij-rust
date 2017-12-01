/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.stubs.StubElement
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
            }
            null
        }

        return if (stubChildren != null) {
            stubChildren.asSequence().map { it.psi }
        } else {
            generateSequence(firstChild) { it.nextSibling }
        }.filterIsInstance<RsElement>()
    }

fun RsItemsOwner.processExpandedItems(f: (RsItemElement) -> Boolean): Boolean {
    for (psi in itemsAndMacros) {
        when (psi) {
            is RsMacroCall ->
                for (expanded in psi.expansion.orEmpty()) {
                    if (expanded is RsItemElement && f(expanded)) return true
                }

            is RsItemElement ->
                if (f(psi)) return true
        }
    }

    return false
}

