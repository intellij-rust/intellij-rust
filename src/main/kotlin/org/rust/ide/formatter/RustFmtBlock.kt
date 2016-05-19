package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import org.rust.ide.formatter.impl.*
import org.rust.lang.core.psi.RustCompositeElementTypes.ARG_LIST
import org.rust.lang.core.psi.RustTokenElementTypes.LBRACE

class RustFmtBlock(
    node: ASTNode,
    alignment: Alignment?,
    indent: Indent?,
    wrap: Wrap?,
    ctx: RustFmtBlockContext
) : AbstractRustFmtBlock(node, alignment, indent, wrap, ctx) {

    override fun buildChildren(): List<Block> {
        val anchor = when (node.elementType) {
            ARG_LIST -> Alignment.createAlignment()
            else -> null
        }

        val children = node.getChildren(null)
            .filter { it.textLength > 0 && it.elementType != WHITE_SPACE }
            .map { buildChild(it, anchor) }

        putUserData(INDENT_BETWEEN_FLAT_BLOCK_BRACES, null)

        return children
    }

    private fun buildChild(child: ASTNode, anchor: Alignment?): AbstractRustFmtBlock {
        if (node.isFlatBlock && child.elementType == LBRACE) {
            putUserDataIfAbsent(INDENT_BETWEEN_FLAT_BLOCK_BRACES, true)
        }
        return AbstractRustFmtBlock.createBlock(
            child,
            calcAlignment(child, anchor),
            computeIndent(this, child),
            null,
            ctx)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = computeSpacing(this, child1, child2, ctx)
    override fun getNewChildIndent(childIndex: Int): Indent? = newChildIndent(this, childIndex)

    private fun calcAlignment(child: ASTNode, anchor: Alignment?): Alignment? = when {
        child.isBlockDelim -> null
        else -> anchor
    }
}
