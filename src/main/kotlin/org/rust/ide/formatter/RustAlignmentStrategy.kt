package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

interface RustAlignmentStrategy {
    /**
     * Requests current strategy for alignment to use for given child.
     */
    fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment?

    /**
     * Always returns `null`.
     */
    object NullStrategy : RustAlignmentStrategy {
        override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment? = null
    }

    /**
     * Apply this strategy only when child element is in [tt].
     */
    fun alignIf(vararg tt: IElementType): RustAlignmentStrategy = alignIf(TokenSet.create(*tt))

    /**
     * Apply this strategy only when child element type matches [filterSet].
     */
    fun alignIf(filterSet: TokenSet): RustAlignmentStrategy =
        object : RustAlignmentStrategy {
            override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment? =
                if (child.elementType in filterSet) {
                    this@RustAlignmentStrategy.getAlignment(child, parent, childCtx)
                } else {
                    null
                }
        }

    /**
     * Apply this strategy only when [predicate] passes.
     */
    fun alignIf(predicate: (child: ASTNode, parent: ASTNode?, ctx: RustFmtContext) -> Boolean): RustAlignmentStrategy =
        object : RustAlignmentStrategy {
            override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment? =
                if (predicate(child, parent, childCtx)) {
                    this@RustAlignmentStrategy.getAlignment(child, parent, childCtx)
                } else {
                    null
                }
        }

    /**
     * Returns [NullStrategy] if [condition] is `false`. Useful for making strategies configurable.
     */
    fun alignIf(condition: Boolean): RustAlignmentStrategy =
        if (condition) {
            this
        } else {
            NullStrategy
        }

    companion object {
        /**
         * Always returns [alignment].
         */
        fun wrap(alignment: Alignment = Alignment.createAlignment()): RustAlignmentStrategy =
            object : RustAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment? =
                    alignment
            }

        /**
         * Always returns [RustFmtContext.sharedAlignment]
         */
        fun shared(): RustAlignmentStrategy =
            object : RustAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment? =
                    childCtx.sharedAlignment
            }
    }
}
