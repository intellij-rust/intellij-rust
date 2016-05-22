package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.TokenSet

/**
 * Mimics [com.intellij.formatting.alignment.AlignmentStrategy], but offers more flexible API
 */
interface RustAlignmentStrategy {
    /**
     * Requests current strategy for alignment to use for given child.
     */
    fun getAlignment(child: ASTNode, parent: ASTNode?): Alignment?

    companion object {
        /**
         * Always returns `null`.
         */
        val NULL_STRATEGY = object : RustAlignmentStrategy {
            override fun getAlignment(child: ASTNode, parent: ASTNode?): Alignment? = null
        }

        /**
         * Returns [alignment] when [predicate] passes; otherwise, `null`.
         */
        fun wrapCond(alignment: Alignment = Alignment.createAlignment(),
                     predicate: (child: ASTNode, parent: ASTNode?) -> Boolean): RustAlignmentStrategy =
            object : RustAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?): Alignment? =
                    if (predicate(child, parent)) alignment else null
            }

        /**
         * Returns alignment returned by [alignmentGetter] when [predicate] passes; otherwise, `null`.
         */
        fun lazyWrapCond(alignmentGetter: () -> Alignment?,
                         predicate: (child: ASTNode, parent: ASTNode?) -> Boolean): RustAlignmentStrategy =
            object : RustAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?): Alignment? =
                    if (predicate(child, parent)) alignmentGetter() else null
            }

        /**
         * Returns alignment returned by [alignmentGetter] when child element type matches [filterSet].
         *
         * @param isBlackList   denotes how [filterSet] is interpreted. If set to `false`, the filter is a white list:
         *                      only children which element types are in [filterSet] will get wrapped alignment;
         *                      otherwise, the filter works as a black list: these children will be ignored.
         */
        fun lazyWrapFiltered(alignmentGetter: () -> Alignment?,
                             filterSet: TokenSet, isBlackList: Boolean = false): RustAlignmentStrategy =
            object : RustAlignmentStrategy {
                override fun getAlignment(child: ASTNode, parent: ASTNode?): Alignment? =
                    if ((child.elementType in filterSet) xor isBlackList) alignmentGetter() else null
            }
    }
}
