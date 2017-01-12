package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.rust.ide.formatter.RustFmtContext

class RustMacroArgFmtBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent?,
    private val wrap: Wrap?,
    val ctx: RustFmtContext
) : ASTBlock {
    override fun getNode(): ASTNode = node
    override fun getTextRange(): TextRange = node.textRange
    override fun getAlignment(): Alignment? = alignment
    override fun getIndent(): Indent? = indent
    override fun getWrap(): Wrap? = wrap

    override fun getSubBlocks(): List<Block> = emptyList()

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = Spacing.getReadOnlySpacing()
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(null, null)

    override fun isLeaf(): Boolean = node.firstChildNode == null
    override fun isIncomplete(): Boolean = false
}
