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
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.ext.RsLooplikeExpr
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_GAP

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
        childType == RET_TYPE ||
            (childType == WHERE_CLAUSE && ctx.rustSettings.INDENT_WHERE_CLAUSE) -> Indent.getNormalIndent()

        // Indent blocks excluding braces
        node.isDelimitedBlock -> getIndentIfNotDelim(child, node)

        // Indent flat block contents, excluding closing brace
        node.isFlatBlock ->
            if (childCtx.metLBrace) {
                getIndentIfNotDelim(child, node)
            } else {
                Indent.getNoneIndent()
            }

        //  Indent let declarations
        parentType == LET_DECL -> Indent.getContinuationWithoutFirstIndent()

        //     let _ =
        //     92;
        // =>
        //     let _ =>
        //         92;
        childPsi is RsExpr && (parentType == MATCH_ARM || parentType == CONSTANT) ->
            Indent.getNormalIndent()

        // Indent if-expressions
        parentPsi is RsIfExpr -> Indent.getNoneIndent()

        // Indent loop-expressions
        parentPsi is RsLooplikeExpr -> Indent.getNoneIndent()

        // Indent match-expressions
        parentPsi is RsMatchExpr -> Indent.getNoneIndent()

        // Indent struct literals
        parentPsi is RsStructLiteral -> Indent.getNoneIndent()

        // Indent other expressions (chain calls, binary expressions, ...)
        parentPsi is RsExpr -> Indent.getContinuationWithoutFirstIndent()

        // Where clause bounds
        childType == WHERE_PRED -> Indent.getContinuationWithoutFirstIndent()

        childType == DOC_GAP && child.chars.startsWith('*') -> Indent.getSpaceIndent(1)

        else -> Indent.getNoneIndent()
    }
}

private fun getIndentIfNotDelim(child: ASTNode, parent: ASTNode): Indent =
    if (child.isBlockDelim(parent)) {
        Indent.getNoneIndent()
    } else {
        Indent.getNormalIndent()
    }
