/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*

/**
 * Can contain [RsStmt]s and [RsExpr]s (which are equivalent to RsExprStmt(RsExpr))
 */
val RsBlock.expandedStmts: List<RsExpandedElement>
    get() {
        val stmts = mutableListOf<RsExpandedElement>()
        processExpandedStmtsInternal { stmt ->
            stmts.add(stmt)
            false
        }
        return stmts
    }

private val RsBlock.stmtsAndMacros: Sequence<RsElement>
    get() {
        val parentItem = contextStrict<RsItemElement>()
        val stub = greenStub
        return if (stub != null && parentItem is RsConstant && parentItem.isConst) {
            stub.childrenStubs.asSequence().map { it.psi }
        } else {
            childrenWithLeaves
        }.filterIsInstance<RsElement>()
    }

private fun RsBlock.processExpandedStmtsInternal(processor: (RsExpandedElement) -> Boolean): Boolean {
    return stmtsAndMacros.any { it.processStmt(processor) }
}

private fun RsElement.processStmt(processor: (RsExpandedElement) -> Boolean) = when (this) {
    is RsMacroCall -> processExpansionRecursively(processor)
    is RsExpandedElement -> processor(this)
    else -> false
}
