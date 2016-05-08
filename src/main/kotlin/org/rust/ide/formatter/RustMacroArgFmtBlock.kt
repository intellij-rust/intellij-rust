package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode

class RustMacroArgFmtBlock(
    node: ASTNode,
    alignment: Alignment?,
    indent: Indent?,
    wrap: Wrap?,
    ctx: RustFmtBlockContext
) : AbstractRustFmtBlock(node, alignment, indent, wrap, ctx) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = Spacing.getReadOnlySpacing()

    override fun buildChildren(): List<Block> = emptyList()
}
