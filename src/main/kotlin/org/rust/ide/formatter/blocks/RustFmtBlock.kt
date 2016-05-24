package org.rust.ide.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import org.rust.ide.formatter.RustAlignmentStrategy
import org.rust.ide.formatter.RustFmtContext
import org.rust.ide.formatter.impl.*
import org.rust.lang.core.psi.RustCompositeElementTypes.METHOD_CALL_EXPR
import org.rust.lang.core.psi.RustTokenElementTypes.DOT
import org.rust.lang.core.psi.util.containsEOL

class RustFmtBlock(
    node: ASTNode,
    alignment: Alignment?,
    indent: Indent?,
    wrap: Wrap?,
    ctx: RustFmtContext
) : AbstractRustFmtBlock(node, alignment, indent, wrap, ctx) {

    override fun buildChildren(): List<Block> {
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

    private fun buildChild(child: ASTNode, alignment: RustAlignmentStrategy): AbstractRustFmtBlock {
        if (node.isFlatBlock && child.isBlockDelim(node)) {
            putUserData(INDENT_MET_LBRACE, true)
        }

        val block = createBlock(child, alignment.getAlignment(child, node), computeIndent(child), null, ctx)

        // Pass shared alignment object to parameter list
        if (node.elementType in FN_DECLS && child.elementType in PARAMS_LIKE) {
            block.putUserData(PARAMETERS_ALIGNMENT, getUserData(PARAMETERS_ALIGNMENT))
        }

        return block
    }
}
