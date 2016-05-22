package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.lang.ASTNode

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
        fun wrap(alignment: Alignment = Alignment.createAlignment(),
                 predicate: (child: ASTNode, parent: ASTNode?) -> Boolean = { c, p -> true })
            : RustAlignmentStrategy = SharedAlignmentStrategyWithPredicate(alignment, predicate)
    }
}

private class SharedAlignmentStrategyWithPredicate(
    private val alignment: Alignment,
    private val predicate: (ASTNode, ASTNode?) -> Boolean
) : RustAlignmentStrategy {
    override fun getAlignment(child: ASTNode, parent: ASTNode?): Alignment? =
        if (predicate(child, parent)) {
            alignment
        } else {
            null
        }
}
