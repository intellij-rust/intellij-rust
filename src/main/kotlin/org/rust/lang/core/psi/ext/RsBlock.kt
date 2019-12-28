/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.expandedFromSequence
import org.rust.lang.core.psi.*

/**
 * Can contain [RsStmt]s and [RsExpr]s (which are equivalent to RsExprStmt(RsExpr))
 */
val RsBlock.expandedStmtsAndTailExpr: Pair<List<RsExpandedElement>, RsExpr?>
    get() {
        val stmts = mutableListOf<RsExpandedElement>()
        processExpandedStmtsInternal { stmt ->
            stmts.add(stmt)
            false
        }
        val tailExpr = stmts.lastOrNull()
            ?.let { it as? RsExpr }
            ?.takeIf { e ->
                // If tail expr is expanded from a macro, we should check that this macro doesn't have
                // semicolon (`foo!();`), otherwise it's not a tail expr but a regular statement
                e.expandedFromSequence.all {
                    val bracesKind = it.bracesKind ?: return@all false
                    !bracesKind.needsSemicolon || it.semicolon == null
                }
            }
        return when (tailExpr) {
            null -> stmts
            else -> stmts.subList(0, stmts.size - 1)
        } to tailExpr
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
