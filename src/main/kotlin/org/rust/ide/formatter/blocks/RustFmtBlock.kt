package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.FormatterUtil
import org.rust.ide.formatter.RustFmtContext
import org.rust.ide.formatter.RustFormattingModelBuilder
import org.rust.ide.formatter.impl.*
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
        val sharedAlignment = when (node.elementType) {
            in FN_DECLS ->
                // Do not align single-line parameter lists in order to avoid producing this:
                // foo(......) -> ret
                //             where ... {}
                if (node.findChildByType(PARAMS_LIKE)?.containsEOL() ?: false) {
                    Alignment.createAlignment()
                } else {
                    null
                }
            in PARAMS_LIKE -> ctx.sharedAlignment
            METHOD_CALL_EXPR -> ctx.sharedAlignment ?: Alignment.createAlignment()
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

                RustFormattingModelBuilder.createBlock(
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
            val dotIndex = children.indexOfFirst { it.node.elementType == DOT }
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
}
