/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.impl

import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import org.rust.ide.formatter.RsFmtContext
import org.rust.ide.formatter.blocks.RsFmtBlock
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsExpr

fun RsFmtBlock.computeIndent(child: ASTNode, childCtx: RsFmtContext): Indent? {
    val parentType = node.elementType
    val parentPsi = node.psi
    val childType = child.elementType
    val childPsi = child.psi
    return when {
    // fn moo(...)
    // -> ...
    // where ... {}
    // =>
    // fn moo(...)
    //     -> ...
    //     where ... {}
        childType == RET_TYPE || childType == WHERE_CLAUSE -> Indent.getNormalIndent()

    // Indent blocks excluding braces
        node.isDelimitedBlock -> getIndentIfNotDelim(child, node)

    // Indent flat block contents, excluding closing brace
        node.isFlatBlock ->
            if (childCtx.metLBrace) {
                getIndentIfNotDelim(child, node)
            } else {
                Indent.getNoneIndent()
            }

    //     let_ =
    //     92;
    // =>
    //     let _ =>
    //         92;
        childPsi is RsExpr && (parentType == MATCH_ARM || parentType == LET_DECL || parentType == CONSTANT) ->
            Indent.getNormalIndent()

    // Indent expressions (chain calls, binary expressions, ...)
        parentPsi is RsExpr -> Indent.getContinuationWithoutFirstIndent()

    // Where clause bounds
        childType == WHERE_PRED -> Indent.getContinuationWithoutFirstIndent()

        else -> Indent.getNoneIndent()
    }
}

private fun getIndentIfNotDelim(child: ASTNode, parent: ASTNode): Indent =
    if (child.isBlockDelim(parent)) {
        Indent.getNoneIndent()
    } else {
        Indent.getNormalIndent()
    }
