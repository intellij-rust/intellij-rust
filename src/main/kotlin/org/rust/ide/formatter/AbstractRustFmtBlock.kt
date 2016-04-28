package org.rust.ide.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.MACRO_ARG

abstract class AbstractRustFmtBlock(
    node: ASTNode,
    alignment: Alignment?,
    private val myIndent: Indent?,
    wrap: Wrap?,
    val ctx: RustFmtBlockContext
) : AbstractBlock(node, wrap, alignment) {

    override fun getIndent(): Indent? = myIndent
    override fun isLeaf(): Boolean = node.firstChildNode == null

    // Tell inheritors, that immutable lists are ok
    abstract override fun buildChildren(): List<Block>

    companion object {
        fun createBlock(
            node: ASTNode,
            alignment: Alignment?,
            indent: Indent?,
            wrap: Wrap?,
            ctx: RustFmtBlockContext
        ): AbstractRustFmtBlock = when (node.elementType) {
            MACRO_ARG -> RustMacroArgFmtBlock(node, alignment, indent, wrap, ctx)
            else  -> RustFmtBlock(node, alignment, indent, wrap, ctx)
        }
    }
}
