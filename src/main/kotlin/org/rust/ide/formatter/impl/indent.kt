package org.rust.ide.formatter.impl

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import org.rust.ide.formatter.RustFmtContext
import org.rust.ide.formatter.blocks.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustTokenElementTypes.LBRACE

fun RustFmtBlock.newChildIndent(childIndex: Int): Indent? = when {
    // Flat brace blocks do not have separate PSI node for content blocks
    // so we have to manually decide whether new child is before (no indent)
    // or after (normal indent) left brace node.
    node.isFlatBraceBlock -> {
        val lbraceIndex = subBlocks.indexOfFirst { it is ASTBlock && it.node.elementType == LBRACE }
        if (lbraceIndex != -1 && lbraceIndex < childIndex) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }
    }

    // We are inside some kind of {...}, [...], (...) or <...> block
    node.isDelimitedBlock -> Indent.getNormalIndent()

    // Indent expressions (chain calls, binary expressions, ...)
    node.psi is RustExprElement -> Indent.getContinuationWithoutFirstIndent()

    // Otherwise we don't want any indentation (null means continuation indent)
    else -> Indent.getNoneIndent()
}

fun RustFmtBlock.computeIndent(child: ASTNode, childCtx: RustFmtContext): Indent? {
    val parentType = node.elementType
    val parentPsi = node.psi
    val childType = child.elementType
    val childPsi = child.psi
    return when {
        // Indent blocks excluding braces
        node.isDelimitedBlock -> getIndentIfNotDelim(child, node)

        // Indent flat block contents, excluding closing brace
        node.isFlatBlock ->
            if (childCtx.metLBrace) {
                getIndentIfNotDelim(child, node)
            } else {
                Indent.getNoneIndent()
            }

        // In match expression:
        //     Foo =>
        //     92
        // =>
        //     Foo =>
        //         92
        parentType == MATCH_ARM && childPsi is RustExprElement -> Indent.getNormalIndent()

        // fn moo(...)
        // -> ...
        // where ... {}
        // =>
        // fn moo(...)
        //     -> ...
        //     where ... {}
        childType == RET_TYPE || childType == WHERE_CLAUSE -> Indent.getNormalIndent()

        // Indent expressions (chain calls, binary expressions, ...)
        parentPsi is RustExprElement -> Indent.getContinuationWithoutFirstIndent()

        else -> Indent.getNoneIndent()
    }
}

private fun getIndentIfNotDelim(child: ASTNode, parent: ASTNode): Indent =
    if (child.isBlockDelim(parent)) {
        Indent.getNoneIndent()
    } else {
        Indent.getNormalIndent()
    }
