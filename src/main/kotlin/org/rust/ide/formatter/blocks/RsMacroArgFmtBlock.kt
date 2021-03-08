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
import org.rust.ide.formatter.impl.isWhitespaceOrEmpty
import org.rust.lang.core.psi.MacroBraces
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf

class RsMacroArgFmtBlock(
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
        return node.getChildren(null)
            .filter { !it.isWhitespaceOrEmpty() }
            .map { childNode: ASTNode ->
                RsFormattingModelBuilder.createBlock(
                    node = childNode,
                    alignment = null,
                    indent = computeIndentForChild(childNode),
                    wrap = null,
                    ctx = ctx)
            }
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return Spacing.getReadOnlySpacing()
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val indent = when (node.elementType) {
            in SUBTREES -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
        return ChildAttributes(indent, null)
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun isIncomplete(): Boolean = myIsIncomplete
    private val myIsIncomplete: Boolean by lazy { FormatterUtil.isIncomplete(node) }

    private fun computeIndentForChild(child: ASTNode): Indent? {
        return when (node.elementType) {
            in SUBTREES -> if (MacroBraces.fromToken(child.elementType) == null) {
                Indent.getNormalIndent()
            } else {
                Indent.getNoneIndent()
            }
            else -> Indent.getNoneIndent()
        }
    }

    companion object {
        private val SUBTREES = tokenSetOf(MACRO_ARGUMENT, MACRO_ARGUMENT_TT, MACRO_BODY, MACRO_PATTERN, MACRO_EXPANSION)
    }
}
