/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.rust.ide.formatter.RsFmtContext
import org.rust.ide.formatter.impl.computeSpacing

/**
 * Synthetic formatting block wraps a subsequence of sub blocks
 * and presents itself as one of the members of this subsequence.
 */
class SyntheticRsFmtBlock(
    val representative: ASTBlock? = null,
    private val subBlocks: List<Block>,
    private val alignment: Alignment? = null,
    private val indent: Indent? = null,
    private val wrap: Wrap? = null,
    val ctx: RsFmtContext
) : ASTBlock {

    init {
        assert(subBlocks.isNotEmpty()) { "tried to build empty synthetic block" }
    }

    private val textRange = TextRange(
        subBlocks.first().textRange.startOffset,
        subBlocks.last().textRange.endOffset)

    override fun getTextRange(): TextRange = textRange

    override fun getNode(): ASTNode? = representative?.node

    override fun getAlignment(): Alignment? = alignment ?: representative?.alignment
    override fun getIndent(): Indent? = indent ?: representative?.indent
    override fun getWrap(): Wrap? = wrap ?: representative?.wrap

    override fun getSubBlocks(): List<Block> = subBlocks

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(indent, null)
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = computeSpacing(child1, child2, ctx)

    override fun isLeaf(): Boolean = false
    override fun isIncomplete(): Boolean = subBlocks.last().isIncomplete

    override fun toString(): String {
        val text = findFirstNonSyntheticChild()?.psi?.containingFile?.text?.let { textRange.subSequence(it) }
            ?: "<rust synthetic>"
        return "$text $textRange"
    }

    private fun findFirstNonSyntheticChild(): ASTNode? {
        val child = subBlocks.first()
        return when (child) {
            is SyntheticRsFmtBlock -> child.findFirstNonSyntheticChild()
            is ASTBlock -> child.node
            else -> null
        }
    }
}
