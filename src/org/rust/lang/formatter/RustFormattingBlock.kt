package org.rust.lang.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RustTokenElementTypes.LBRACE
import org.rust.lang.core.lexer.RustTokenElementTypes.RBRACE
import org.rust.lang.core.psi.RustCompositeElementTypes.*

class RustFormattingBlock(private val node: ASTNode, private val indent: Indent?) : ASTBlock {

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val childIndent = if (BLOCKS_TOKEN_SET.contains(node.elementType)) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }

        return ChildAttributes(childIndent, null)
    }

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
        val blockStart = node.findChildByType(LBRACE)?.startOffset ?: 0
        node.getChildren(null)
                .filter { it.textRange.length > 0 && it.elementType != WHITE_SPACE }
                .map {
                    RustFormattingBlock(it, calcIndent(it, blockStart))
                }
    }

    private fun calcIndent(child: ASTNode, blockStart: Int): Indent {
        if (!BLOCKS_TOKEN_SET.contains(node.elementType)) {
            return Indent.getNoneIndent()
        }
        if (BRACES_TOKEN_SET.contains(child.elementType)) {
            return Indent.getNoneIndent()
        }

        // TODO(matklad): change PSI structure and get rid of this abomination
        if (child.startOffset < blockStart) {
            return Indent.getNoneIndent()
        }

        return Indent.getNormalIndent()
    }
}

private val BLOCKS_TOKEN_SET = TokenSet.create(
        BLOCK,
        INNER_ATTRS_AND_BLOCK,
        MOD_ITEM,
        ENUM_ITEM,
        STRUCT_DECL_ARGS,
        IMPL_ITEM
)

private val BRACES_TOKEN_SET = TokenSet.create(
        LBRACE, RBRACE
)
