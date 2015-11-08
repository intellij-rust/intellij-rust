package org.rust.lang.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.lexer.RustTokenElementTypes.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*

class RustFormattingBlock(private val node: ASTNode,
                          private val indent: Indent = Indent.getNoneIndent(),
                          private val alignment: Alignment? = null) : ASTBlock {

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val childIndent = if (node.elementType in BLOCKS_TOKEN_SET) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }

        return ChildAttributes(childIndent, null)
    }

    override fun getNode() = node
    override fun getAlignment() = alignment
    override fun getIndent() = indent
    override fun getWrap() = null
    override fun getTextRange() = node.textRange

    override fun isIncomplete() = false
    override fun isLeaf() = node.firstChildNode == null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getSubBlocks(): List<Block> = mySubBlock

    private val mySubBlock: List<Block> by lazy {
        val anchor = if (node.elementType == ARG_LIST) {
            Alignment.createAlignment()
        } else {
            null
        }

        node.getChildren(null)
                .filter { it.textRange.length > 0 && it.elementType != WHITE_SPACE }
                .map {
                    RustFormattingBlock(it,
                            calcChildIndent(it),
                            calcAlignment(it, anchor))
                }
    }

    private fun calcAlignment(child: ASTNode, anchor: Alignment?): Alignment? =
        when (child.elementType) {
            in BRACES_TOKEN_SET -> null
            else                -> anchor
        }


    private fun calcChildIndent(child: ASTNode): Indent {
        val parentType = node.elementType
        val childType = child.elementType

        if (parentType in BLOCKS_TOKEN_SET) {
            if (childType in BLOCK_START_TOKEN_SET || childType in BRACES_TOKEN_SET) {
                return Indent.getNoneIndent()
            }
            return Indent.getNormalIndent()
        }

        return Indent.getNoneIndent()
    }
}

private val BLOCK_START_TOKEN_SET = TokenSet.create(
        PUB,
        MOD,
        STRUCT,
        ENUM,
        IMPL,
        TRAIT,
        MATCH
)

private val BLOCKS_TOKEN_SET = TokenSet.create(
        BLOCK,
        MOD_ITEM,
        ENUM_ITEM,
        STRUCT_DECL_ARGS,
        TRAIT_ITEM,
        IMPL_ITEM,
        STRUCT_EXPR_BODY,
        MATCH_EXPR
)

private val BRACES_TOKEN_SET = TokenSet.create(
        LBRACE, RBRACE,
        LPAREN, RPAREN
)
