package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.formatter.FormatterUtil
import org.rust.lang.core.psi.RustCompositeElementTypes.MACRO_ARG

/**
 * Inspired by [com.intellij.psi.formatter.common.AbstractBlock], but rewritten for better flexibility.
 */
abstract class AbstractRustFmtBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent?,
    private val wrap: Wrap?,
    val ctx: RustFmtBlockContext
) : UserDataHolderBase(), ASTBlock {

    override fun getNode(): ASTNode = node
    override fun getTextRange(): TextRange = node.textRange
    override fun getAlignment(): Alignment? = alignment
    override fun getIndent(): Indent? = indent
    override fun getWrap(): Wrap? = wrap

    override fun getSubBlocks(): List<Block> = mySubBlocks
    private val mySubBlocks: List<Block> by lazy { buildChildren() }

    abstract fun buildChildren(): List<Block>

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        ChildAttributes(getNewChildIndent(newChildIndex), getNewChildAlignment(newChildIndex))

    protected open fun getNewChildIndent(childIndex: Int): Indent? = null
    protected open fun getNewChildAlignment(childIndex: Int): Alignment? = null

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
            ctx: RustFmtBlockContext
        ): AbstractRustFmtBlock = when (node.elementType) {
            MACRO_ARG -> RustMacroArgFmtBlock(node, alignment, indent, wrap, ctx)
            else -> RustFmtBlock(node, alignment, indent, wrap, ctx)
        }
    }
}
