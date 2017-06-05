package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
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

        val joinStructLastLine = tryJoinStructLastLine(file, start)
        if (joinStructLastLine != CANNOT_JOIN) return joinStructLastLine

        return tryJoinSingleExpressionBlock(file, start)
    }

    private fun getCurrentField(file: RsFile, start: Int): PsiElement? {
        var elem = file.findElementAt(start) ?: return null
        while (elem != null && elem.elementType != STRUCT_LITERAL_FIELD) elem = elem.prevSibling
        return elem
    }

    fun tryJoinStructLastLine(file: RsFile, start: Int): Int {
        val struct = file.findElementAt(start)?.parentOfType<RsStructLiteral>() ?: return CANNOT_JOIN
        val lastField = struct.structLiteralBody.structLiteralFieldList.last() ?: return CANNOT_JOIN
        val currentField = getCurrentField(file, start) ?: return CANNOT_JOIN

        if (currentField != lastField) return CANNOT_JOIN

        val comma = currentField.nextSibling
        if (comma.elementType == COMMA) comma.delete()
        else return CANNOT_JOIN

        val whitespace = struct.structLiteralBody.children.last().nextSibling
        if (whitespace is PsiWhiteSpace) whitespace.delete()

        return currentField.textRange.endOffset
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

    override fun tryJoinLines(document: Document?, file: PsiFile?, start: Int, end: Int): Int = CANNOT_JOIN
}
