package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.FormatterUtil
import org.rust.ide.formatter.RustFmtContext
import org.rust.ide.formatter.impl.*
import org.rust.lang.core.psi.RustCompositeElementTypes.MACRO_ARG
import org.rust.lang.core.psi.RustCompositeElementTypes.METHOD_CALL_EXPR
import org.rust.lang.core.psi.RustTokenElementTypes.DOT
import org.rust.lang.core.psi.util.containsEOL

class RustFmtBlock(
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

    override fun getSubBlocks(): List<Block> = mySubBlocks
    private val mySubBlocks: List<Block> by lazy { buildChildren() }

    private fun buildChildren(): List<Block> {
        // Create shared alignment object for function/method definitions,
        // which parameter lists span multiple lines. This way we will be
        // able to align return type and where clause properly.
        val sharedAlignment = when {
            node.elementType in FN_DECLS && node.findChildByType(PARAMS_LIKE)?.containsEOL() ?: false ->
                Alignment.createAlignment()

            node.elementType in PARAMS_LIKE -> ctx.sharedAlignment
            else -> null
        }
        var metLBrace = false
        val alignment = getAlignmentStrategy()

        val children = node.getChildren(null)
            .filter { !it.isWhitespaceOrEmpty() }
            .map { childNode: ASTNode ->
                if (node.isFlatBlock && childNode.isBlockDelim(node)) {
                    metLBrace = true
                }

                childNode to ctx.copy(
                    metLBrace = metLBrace,
                    sharedAlignment = when {
                        // Pass shared alignment only to PARAMS_LIKE, RET_TYPE and WHERE_CLAUSE
                        node.elementType in FN_DECLS && childNode.elementType !in FN_SHARED_ALIGN_OWNERS -> null
                        else -> sharedAlignment
                    })
            }
            .map {
                val (childNode, childCtx) = it
                createBlock(
                    node = childNode,
                    alignment = alignment.getAlignment(childNode, node, childCtx),
                    indent = computeIndent(childNode, childCtx),
                    wrap = null,
                    ctx = childCtx)
            }

        // Create fake `.sth` block here, so child indentation will
        // be relative to it when it starts from new line.
        // In other words: foo().bar().baz() => foo().baz()[.baz()]
        // We are using dot as our representative.
        // The idea is nearly copy-pasted from Kotlin's formatter.
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

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = computeSpacing(child1, child2, ctx)

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        ChildAttributes(newChildIndent(newChildIndex), null)

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun isIncomplete(): Boolean = myIsIncomplete
    private val myIsIncomplete: Boolean by lazy { FormatterUtil.isIncomplete(node) }

    override fun toString() = "${node.text} $textRange"

    companion object {
        fun createBlock(
            node: ASTNode,
            alignment: Alignment?,
            indent: Indent?,
            wrap: Wrap?,
            ctx: RustFmtContext
        ): ASTBlock = when (node.elementType) {
            MACRO_ARG -> RustMacroArgFmtBlock(node, alignment, indent, wrap, ctx)
            else -> RustFmtBlock(node, alignment, indent, wrap, ctx)
        }
    }
}
