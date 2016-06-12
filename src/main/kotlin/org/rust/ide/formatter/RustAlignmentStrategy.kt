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
     *
     * @param isBlackList   denotes how filter set is interpreted. If set to `false`, the filter is a white list:
     *                      only children which element types are in filter set will get wrapped alignment;
     *                      otherwise, the filter works as a black list: these children will be ignored.
     */
    fun filter(vararg tt: IElementType, isBlackList: Boolean = false): RustAlignmentStrategy =
        filter(TokenSet.create(*tt), isBlackList)

    /**
     * Apply this strategy only when child element type matches [filterSet].
     *
     * @param isBlackList   denotes how [filterSet] is interpreted. If set to `false`, the filter is a white list:
     *                      only children which element types are in [filterSet] will get wrapped alignment;
     *                      otherwise, the filter works as a black list: these children will be ignored.
     */
    fun filter(filterSet: TokenSet, isBlackList: Boolean = false): RustAlignmentStrategy =
        object : RustAlignmentStrategy {
            override fun getAlignment(child: ASTNode, parent: ASTNode?, childCtx: RustFmtContext): Alignment? =
                if ((child.elementType in filterSet) xor isBlackList) {
                    this@RustAlignmentStrategy.getAlignment(child, parent, childCtx)
                } else {
                    null
                }
        }

    /**
     * Apply this strategy only when [predicate] passes.
     */
    fun cond(predicate: (child: ASTNode, parent: ASTNode?, ctx: RustFmtContext) -> Boolean): RustAlignmentStrategy =
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
    fun cfg(condition: Boolean): RustAlignmentStrategy =
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
