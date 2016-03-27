package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.MACRO_ARG

abstract class AbstractRustBlock(
    node: ASTNode,
    alignment: Alignment?,
    private val myIndent: Indent?,
    wrap: Wrap?,
    val ctx: RustBlockContext
) : AbstractBlock(node, wrap, alignment), ASTBlock {
    override fun getIndent(): Indent? = myIndent
    override fun isLeaf(): Boolean = node.firstChildNode == null
    abstract override fun buildChildren(): List<Block>

    companion object {
        fun createBlock(
            node: ASTNode,
            alignment: Alignment?,
            indent: Indent?,
            wrap: Wrap?,
            ctx: RustBlockContext
        ): AbstractRustBlock = when (node.elementType) {
            MACRO_ARG -> RustMacroArgBlock(node, alignment, indent, wrap, ctx)
            else  -> RustBlock(node, alignment, indent, wrap, ctx)
        }
    }
}
