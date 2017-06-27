/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

interface RsAlignmentStrategy {
    /**
     * Requests current strategy for alignment to use for given child.
     */
    fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RsFmtContext): Alignment?

    /**
     * Always returns `null`.
     */
    object NullStrategy : RsAlignmentStrategy {
        override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RsFmtContext): Alignment? = null
    }

    /**
     * Apply this strategy only when child element is in [tt].
     */
    fun alignIf(vararg tt: IElementType): RsAlignmentStrategy = alignIf(TokenSet.create(*tt))

    /**
     * Apply this strategy only when child element type matches [filterSet].
     */
    fun alignIf(filterSet: TokenSet): RsAlignmentStrategy =
        object : RsAlignmentStrategy {
            override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RsFmtContext): Alignment? =
                if (child.elementType in filterSet) {
                    this@RsAlignmentStrategy.getAlignment(child, parent, childCtx)
                } else {
                    null
                }
        }

    /**
     * Apply this strategy only when [predicate] passes.
     */
    fun alignIf(predicate: (child: ASTNode, parent: ASTNode?, ctx: RsFmtContext) -> Boolean): RsAlignmentStrategy =
        object : RsAlignmentStrategy {
            override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RsFmtContext): Alignment? =
                if (predicate(child, parent, childCtx)) {
                    this@RsAlignmentStrategy.getAlignment(child, parent, childCtx)
                } else {
                    null
                }
        }

    /**
     * Returns [NullStrategy] if [condition] is `false`. Useful for making strategies configurable.
     */
    fun alignIf(condition: Boolean): RsAlignmentStrategy =
        if (condition) {
            this
        } else {
            NullStrategy
        }

    companion object {
        /**
         * Always returns [alignment].
         */
        fun wrap(alignment: Alignment = Alignment.createAlignment()): RsAlignmentStrategy =
            object : RsAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RsFmtContext): Alignment? =
                    alignment
            }

        /**
         * Always returns [RsFmtContext.sharedAlignment]
         */
        fun shared(): RsAlignmentStrategy =
            object : RsAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RsFmtContext): Alignment? =
                    childCtx.sharedAlignment
            }
    }
}
