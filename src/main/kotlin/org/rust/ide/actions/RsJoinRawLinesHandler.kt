/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.rust.lang.core.parser.RustParserDefinition
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.LBRACE
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.ext.containingDoc

class RsJoinRawLinesHandler : JoinRawLinesHandlerDelegate {
    /**
     *  Executed when user presses `Ctrl+Shift+J`, before lines are joined.
     *  See [RsJoinLinesHandler]
     */
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is RsFile) return CANNOT_JOIN
        if (start == 0) return CANNOT_JOIN

        val tryJoinSingleExpressionBlock = tryJoinSingleExpressionBlock(file, start)
        if (tryJoinSingleExpressionBlock != CANNOT_JOIN) return tryJoinSingleExpressionBlock

        val leftPsi = file.findElementAt(start)?.containingDoc ?: return CANNOT_JOIN
        val rightPsi = file.findElementAt(end)?.containingDoc ?: return CANNOT_JOIN

        if (leftPsi != rightPsi) return CANNOT_JOIN

        return when (leftPsi.elementType) {
            RustParserDefinition.INNER_EOL_DOC_COMMENT, RustParserDefinition.OUTER_EOL_DOC_COMMENT ->
                joinLineDocComment(document, start, end)

            else -> CANNOT_JOIN
        }
    }

    // Normally this is handled by `CodeDocumentationAwareCommenter`, but Rust have different styles
    // of documentation comments, so we handle this manually.
    private fun joinLineDocComment(document: Document, start: Int, end: Int): Int {
        val prefix = document.charsSequence.subSequence(end, end + 3).toString()
        if (prefix != "///" && prefix != "//!") return CANNOT_JOIN
        document.deleteString(start, end + prefix.length)
        return start
    }

    private fun tryJoinSingleExpressionBlock(file: RsFile, start: Int): Int {
        val lbrace = file.findElementAt(start - 1)!!
        if (lbrace.elementType != LBRACE) return CANNOT_JOIN

        val block = lbrace.parent as? RsBlock ?: return CANNOT_JOIN

        val expr = block.expr ?: return CANNOT_JOIN
        if (expr.getPrevNonCommentSibling() != lbrace) return CANNOT_JOIN
        if (block.node.getChildren(RS_COMMENTS).isNotEmpty()) {
            return CANNOT_JOIN
        }

        val psiFactory = RsPsiFactory(file.project)
        when (val parent = block.parent) {
            is RsBlockExpr -> {
                return when {
                    parent.isUnsafe || parent.isAsync || parent.isTry -> CANNOT_JOIN
                    else -> {
                        val grandpa = parent.parent
                        val newExpr = parent.replace(expr)
                        if (grandpa is RsMatchArm && grandpa.lastChild?.elementType != COMMA) {
                            grandpa.add(psiFactory.createComma())
                        }
                        newExpr.startOffset
                    }
                }

            }

            is RsIfExpr, is RsElseBranch -> {
                val newBlock = psiFactory.createBlockExpr(expr.text).block
                return block.replace(newBlock).startOffset
            }
        }

        return CANNOT_JOIN
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = CANNOT_JOIN
}
