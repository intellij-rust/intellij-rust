package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.formatter.FormatterUtil
import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.RustFmtContext
import org.rust.ide.formatter.impl.*
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustCompositeElementTypes.MACRO_ARG
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.util.containsEOL

class RustFmtBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent?,
    private val wrap: Wrap?,
    val ctx: RustFmtContext
) : UserDataHolderBase(), ASTBlock {

    override fun getNode(): ASTNode = node
    override fun getTextRange(): TextRange = node.textRange
    override fun getAlignment(): Alignment? = alignment
    override fun getIndent(): Indent? = indent
    override fun getWrap(): Wrap? = wrap

    override fun getSubBlocks(): List<Block> = mySubBlocks
    private val mySubBlocks: List<Block> by lazy { buildChildren() }

    fun buildChildren(): List<Block> {
        // Create shared alignment object for function/method definitions,
        // which parameter lists span multiple lines. This way we will be
        // able to align return type and where clause properly.
        if (node.elementType in FN_DECLS && node.findChildByType(PARAMS_LIKE)?.containsEOL() ?: false) {
            putUserDataIfAbsent(PARAMETERS_ALIGNMENT, Alignment.createAlignment())
        }

        val alignment = getAlignmentStrategy()

        val children = node.getChildren(null)
            .filter { !it.isWhitespaceOrEmpty() }
            .map { buildChild(it, alignment) }

        putUserData(INDENT_MET_LBRACE, null)
        putUserData(PARAMETERS_ALIGNMENT, null)

        // Create fake `.sth` block here, so child indentation will
        // be relative to it when it starts from new line.
        // In other words: foo().bar().baz() => foo().baz()[.baz()]
        // We are using dot as our representative.
        // The idea is nearly copy-pasted from Kotlin's formatter.
        if (node.elementType == RustCompositeElementTypes.METHOD_CALL_EXPR) {
            val dotIndex = children.indexOfFirst { it is ASTBlock && it.node.elementType == RustTokenElementTypes.DOT }
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

    private fun buildChild(child: ASTNode, alignment: RustAlignmentStrategy): ASTBlock {
        if (node.isFlatBlock && child.isBlockDelim(node)) {
            putUserData(INDENT_MET_LBRACE, true)
        }

        val block = createBlock(child, alignment.getAlignment(child, node), computeIndent(child), null, ctx)

        // Pass shared alignment object to parameter list
        if (block is RustFmtBlock && node.elementType in FN_DECLS && child.elementType in PARAMS_LIKE) {
            block.putUserData(PARAMETERS_ALIGNMENT, getUserData(PARAMETERS_ALIGNMENT))
        }

        return block
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
