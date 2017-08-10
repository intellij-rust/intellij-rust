/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.FormatterUtil
import org.rust.ide.formatter.RsFmtContext
import org.rust.ide.formatter.RsFormattingModelBuilder
import org.rust.ide.formatter.impl.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsExpr

class RsFmtBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent?,
    private val wrap: Wrap?,
    val ctx: RsFmtContext
) : ASTBlock {

    override fun getNode(): ASTNode = node
    override fun getTextRange(): TextRange = node.textRange
    override fun getAlignment(): Alignment? = alignment
    override fun getIndent(): Indent? = indent
    override fun getWrap(): Wrap? = wrap

    override fun getSubBlocks(): List<Block> = mySubBlocks
    private val mySubBlocks: List<Block> by lazy { buildChildren() }

    private fun buildChildren(): List<Block> {
        val sharedAlignment = when (node.elementType) {
            in FN_DECLS -> Alignment.createAlignment()
            VALUE_PARAMETER_LIST -> ctx.sharedAlignment
            DOT_EXPR ->
                if (node.treeParent.elementType == DOT_EXPR)
                    ctx.sharedAlignment
                else
                    Alignment.createAlignment()
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

                val childCtx = ctx.copy(
                    metLBrace = metLBrace,
                    sharedAlignment = sharedAlignment)

                RsFormattingModelBuilder.createBlock(
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
        if (node.elementType == DOT_EXPR) {
            val dotIndex = children.indexOfFirst { it.node.elementType == DOT }
            if (dotIndex != -1) {
                val dotBlock = children[dotIndex]
                val syntheticBlock = SyntheticRsFmtBlock(
                    representative = dotBlock,
                    subBlocks = children.subList(dotIndex, children.size),
                    ctx = ctx)
                return children.subList(0, dotIndex).plusElement(syntheticBlock)
            }
        }

        return children
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = computeSpacing(child1, child2, ctx)

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        if (CommaList.forElement(node.elementType) != null && newChildIndex > 1) {
            val isBeforeClosingBrace = newChildIndex + 1 == subBlocks.size
            return if (isBeforeClosingBrace)
                ChildAttributes.DELEGATE_TO_PREV_CHILD
            else
                ChildAttributes.DELEGATE_TO_NEXT_CHILD
        }

        val indent = when {
        // Flat brace blocks do not have separate PSI node for content blocks
        // so we have to manually decide whether new child is before (no indent)
        // or after (normal indent) left brace node.
            node.isFlatBraceBlock -> {
                val lbraceIndex = subBlocks.indexOfFirst { it is ASTBlock && it.node.elementType == LBRACE }
                if (lbraceIndex != -1 && lbraceIndex < newChildIndex) {
                    Indent.getNormalIndent()
                } else {
                    Indent.getNoneIndent()
                }
            }

        // We are inside some kind of {...}, [...], (...) or <...> block
            node.isDelimitedBlock -> Indent.getNormalIndent()

        // Indent expressions (chain calls, binary expressions, ...)
            node.psi is RsExpr -> Indent.getContinuationWithoutFirstIndent()

        // Otherwise we don't want any indentation (null means continuation indent)
            else -> Indent.getNoneIndent()
        }
        return ChildAttributes(indent, null)
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun isIncomplete(): Boolean = myIsIncomplete
    private val myIsIncomplete: Boolean by lazy { FormatterUtil.isIncomplete(node) }

    override fun toString() = "${node.text} $textRange"
}
