package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import org.rust.ide.formatter.impl.*
import org.rust.lang.core.psi.RustCompositeElementTypes.ARG_LIST
import org.rust.lang.core.psi.RustCompositeElementTypes.METHOD_CALL_EXPR
import org.rust.lang.core.psi.RustTokenElementTypes.DOT

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

        putUserData(INDENT_MET_LBRACE, null)

        // Create fake `.sth` block here, so child indentation will
        // be relative to it when it starts from new line.
        // In other words: foo().bar().baz() => foo().baz()[.baz()]
        // Nearly copy-pasted idea from Kotlin's formatter.
        if (node.elementType == METHOD_CALL_EXPR) {
            val dotIndex = children.indexOfFirst { it is ASTBlock && it.node.elementType == DOT }
            if (dotIndex != -1) {
                val dotBlock = children[dotIndex]
                val syntheticBlock = SyntheticRustFmtBlock(
                    representative = dotBlock,
                    subBlocks = children.subList(dotIndex, children.size),
                    ctx = ctx)
                return children.subList(0, dotIndex).plusElement(syntheticBlock)
            }
        }

        return children
    }

    private fun buildChild(child: ASTNode, anchor: Alignment?): AbstractRustFmtBlock {
        if (node.isFlatBlock && child.isBlockDelim(node)) {
            replace(INDENT_MET_LBRACE, null, true)
        }

        return AbstractRustFmtBlock.createBlock(
            child,
            calcAlignment(child, anchor),
            computeIndent(child),
            null,
            ctx)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = computeSpacing(child1, child2, ctx)
    override fun getNewChildIndent(childIndex: Int): Indent? = newChildIndent(childIndex)

    private fun calcAlignment(child: ASTNode, anchor: Alignment?): Alignment? = when {
        child.isBlockDelim -> null
        else -> anchor
    }
}
