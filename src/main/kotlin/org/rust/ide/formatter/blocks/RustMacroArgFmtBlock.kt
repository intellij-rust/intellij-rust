package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import org.rust.ide.formatter.RustFmtContext

class RustMacroArgFmtBlock(
    node: ASTNode,
    alignment: Alignment?,
    indent: Indent?,
    wrap: Wrap?,
    ctx: RustFmtContext
) : AbstractRustFmtBlock(node, alignment, indent, wrap, ctx) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = Spacing.getReadOnlySpacing()

    override fun buildChildren(): List<Block> = emptyList()
}
