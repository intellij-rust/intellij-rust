package org.rust.ide.formatter.impl

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import org.rust.ide.formatter.RustFmtBlock
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustExpr
import org.rust.lang.core.psi.RustTokenElementTypes.LBRACE

val INDENT_INSIDE_FLAT_BLOCK: Key<Boolean> = Key.create("INDENT_BETWEEN_FLAT_BLOCK_BRACES")

fun ASTBlock.newChildIndent(childIndex: Int): Indent? {
    // MOD_ITEM and FOREIGN_MOD_ITEM do not have separate PSI node for contents
    // blocks so we have to manually decide whether new child is before (no indent)
    // or after (normal indent) left brace node.
    if (node.isModItem) {
        val lbraceIndex = subBlocks.indexOfFirst { it is ASTBlock && it.node.elementType == LBRACE }
        if (lbraceIndex != -1 && lbraceIndex < childIndex) {
            return Indent.getNormalIndent()
        }
    }

    // We are inside some kind of {...}, [...], (...) or <...> block
    if (node.isDelimitedBlock) {
        return Indent.getNormalIndent()
    }

    // Otherwise we don't want any indentation (null means continuation indent)
    return Indent.getNoneIndent()
}

fun RustFmtBlock.computeIndent(child: ASTNode): Indent? {
    val parentType = node.elementType
    val childType = child.elementType
    val childPsi = child.psi
    return when {
        node.isDelimitedBlock -> getIndentIfNotDelim(child, node)

        (node.isFlatBlock || parentType == PAT_ENUM) && getUserData(INDENT_INSIDE_FLAT_BLOCK) == true ->
            getIndentIfNotDelim(child, node)

        parentType == MATCH_ARM && childPsi is RustExpr -> Indent.getNormalIndent()

        childType == RET_TYPE || childType == WHERE_CLAUSE -> Indent.getNormalIndent()

        else -> Indent.getNoneIndent()
    }
}

private fun getIndentIfNotDelim(child: ASTNode, parent: ASTNode): Indent =
    if (child.isBlockDelim(parent)) {
        Indent.getNoneIndent()
    } else {
        Indent.getNormalIndent()
    }
