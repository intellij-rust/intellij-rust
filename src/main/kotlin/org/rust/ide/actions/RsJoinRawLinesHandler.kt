/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.*

class RsJoinRawLinesHandler : JoinRawLinesHandlerDelegate {
    /**
     *  Executed when user presses `Ctrl+Shift+J`, before lines are joined.
     *  See [RsJoinLinesHandler]
     */
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is RsFile) return CANNOT_JOIN
        if (start == 0) return CANNOT_JOIN

        return tryJoinSingleExpressionBlock(file, start)
    }

    fun tryJoinSingleExpressionBlock(file: RsFile, start: Int): Int {
        val lbrace = file.findElementAt(start - 1)!!
        if (lbrace.elementType != LBRACE) return CANNOT_JOIN

        val block = lbrace.parent as? RsBlock ?: return CANNOT_JOIN

        val expr = block.expr ?: return CANNOT_JOIN
        if (expr.getPrevNonCommentSibling() != lbrace) return CANNOT_JOIN
        if (block.node.getChildren(RS_COMMENTS).isNotEmpty()) {
            return CANNOT_JOIN
        }

        val psiFactory = RsPsiFactory(file.project)
        val parent = block.parent
        when (parent) {
            is RsBlockExpr -> {
                val grandpa = parent.parent
                val newExpr = parent.replace(expr)
                if (grandpa is RsMatchArm && grandpa.lastChild?.elementType != COMMA) {
                    grandpa.add(psiFactory.createComma())
                }
                return newExpr.textRange.startOffset
            }

            is RsIfExpr, is RsElseBranch -> {
                val newBlock = psiFactory.createBlockExpr(expr.text).block
                return block.replace(newBlock).textRange.startOffset
            }
        }

        return CANNOT_JOIN
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = CANNOT_JOIN
}
