/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.expandedFromSequence
import org.rust.lang.core.psi.*

/**
 * Returns a statement list and a tail expression (if exists) of the block *after* expansion
 * of macros and `cfg` attributes
 *
 * @see syntaxTailStmt
 */
val RsBlock.expandedStmtsAndTailExpr: Pair<List<RsStmt>, RsExpr?>
    get() {
        val stmts = mutableListOf<RsStmt>()
        processExpandedStmtsInternal { stmt ->
            if (stmt is RsStmt && (stmt !is RsDocAndAttributeOwner || stmt.existsAfterExpansionSelf)) {
                stmts.add(stmt)
            }
            false
        }
        val tailStmt = stmts.lastOrNull()
            ?.let { it as? RsExprStmt }
            ?.takeIf { !it.hasSemicolon }
            ?.takeIf { e ->
                // If tail expr is expanded from a macro, we should check that this macro doesn't have
                // semicolon (`foo!();`), otherwise it's not a tail expr but a regular statement
                e.expandedFromSequence.all {
                    val bracesKind = it.bracesKind ?: return@all false
                    !bracesKind.needsSemicolon || it.semicolon == null
                }
            }

        return when (tailStmt) {
            null -> stmts
            else -> stmts.subList(0, stmts.size - 1)
        } to tailStmt?.expr
    }

val RsBlock.expandedTailExpr: RsExpr?
    get() = expandedStmtsAndTailExpr.second

/**
 * Returns the last [RsExprStmt] without a semicolon (![RsExprStmt.hasSemicolon]) in a block *before* expansion
 * of macros and `cfg` attributes
 * ```
 * // Returns `foo`:
 * {
 *     let foo = 0;
 *     foo
 * }
 * // Returns `{ foo }`:
 * {
 *     let foo = 0;
 *     { foo }
 * }
 * // Returns `null`:
 * {
 *     let foo = 0;
 *     let bar = 0;
 *     #[cfg(unix)]
 *     { foo }
 *     #[cfg(windows)]
 *     { bar }
 * }
 * // Returns `null`:
 * {
 *     foo! {}
 * }
 * ```
 *
 * @see expandedTailExpr
 */
val RsBlock.syntaxTailStmt: RsExprStmt?
    get() {
        val lastStmt = rbrace?.getPrevNonCommentSibling() as? RsExprStmt ?: return null
        return lastStmt.takeIf { !lastStmt.hasSemicolon && !lastStmt.queryAttributes.hasCfgAttr() }
    }

/** For a block like `{ 0 }` returns `0` */
fun RsBlock.singleTailStmt(): RsExprStmt? {
    val tailStmt = syntaxTailStmt ?: return null
    if (tailStmt.getPrevNonCommentSibling() == lbrace) {
        return tailStmt
    }
    return null
}

/** For a block like `{ 0; }` returns `0;`, for a block like `{ 0 }` returns `0` */
fun RsBlock.singleStmt(): RsStmt? {
    val firstStmt = lbrace.getNextNonCommentSibling() as? RsStmt ?: return null
    if (firstStmt.getNextNonCommentSibling() == rbrace) {
        return firstStmt
    }
    return null
}

private val RsBlock.stmtsAndMacros: Sequence<RsElement>
    get() {
        val stub = greenStub

        fun isConstant(): Boolean {
            val parentItem = contextStrict<RsItemElement>()
            return parentItem is RsConstant && parentItem.isConst
        }

        fun isConstExpr(): Boolean {
            return contextStrict<RsTypeArgumentList>() != null
        }

        return if (stub != null && (isConstant() || isConstExpr())) {
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
