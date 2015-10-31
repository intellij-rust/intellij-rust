package org.rust.lang.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RustTokenElementTypes.LBRACE
import org.rust.lang.core.lexer.RustTokenElementTypes.RBRACE
import org.rust.lang.core.psi.RustCompositeElementTypes.BLOCK
import org.rust.lang.core.psi.RustCompositeElementTypes.INNER_ATTRS_AND_BLOCK

class RustFormattingBlock(private val node: ASTNode, private val indent: Indent?) : ASTBlock {

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes(Indent.getNormalIndent(), null)

    override fun getNode() = node
    override fun getAlignment() = null
    override fun getIndent() = indent
    override fun getWrap() = null
    override fun getTextRange() = node.textRange

    override fun isIncomplete() = false
    override fun isLeaf() = node.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getSubBlocks(): List<Block> = mySubBlock

    private val mySubBlock: List<Block> by lazy {
        node.getChildren(null)
                .filter { it.textRange.length > 0 && it.elementType != WHITE_SPACE }
                .map {
                    RustFormattingBlock(it, calcIndent(it))
                }
    }

    private fun calcIndent(child: ASTNode): Indent {
        val parentType = node.elementType
        val type = child.elementType
        if (BLOCKS_TOKEN_SET.contains(parentType) && !BRACES_TOKEN_SET.contains(type)) {
            return Indent.getNormalIndent()
        }
        return Indent.getNoneIndent()
    }
}

private val BLOCKS_TOKEN_SET = TokenSet.create(
        BLOCK, INNER_ATTRS_AND_BLOCK
)
private val BRACES_TOKEN_SET = TokenSet.create(
        LBRACE, RBRACE
)
