package org.rust.ide.formatter

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Alignment
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock

abstract class AbstractRustBlock(
    node: ASTNode,
    alignment: Alignment?,
    private val myIndent: Indent?,
    wrap: Wrap?
) : AbstractBlock(node, wrap, alignment), ASTBlock {
    override fun getIndent(): Indent? = myIndent
    override fun isLeaf(): Boolean = node.firstChildNode == null
}
