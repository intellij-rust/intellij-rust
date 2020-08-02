/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package org.rust.ide.refactoring

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.utils.findExpressionAtCaret
import org.rust.ide.utils.findExpressionInRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestors
import java.util.*

fun findCandidateExpressionsToExtract(editor: Editor, file: RsFile): List<RsExpr> {
    val selection = editor.selectionModel
    return if (selection.hasSelection()) {
        // If there's an explicit selection, suggest only one expression
        listOfNotNull(findExpressionInRange(file, selection.selectionStart, selection.selectionEnd))
    } else {
        val expr = findExpressionAtCaret(file, editor.caretModel.offset)
            ?: return emptyList()
        // Finds possible expressions that might want to be bound to a local variable.
        // We don't go further than the current block scope,
        // further more path expressions don't make sense to bind to a local variable so we exclude them.
        expr.ancestors
            .takeWhile { it !is RsBlock }
            .filterIsInstance<RsExpr>()
            .filter { it !is RsPathExpr }
            .toList()
    }
}

/**
 * Finds occurrences in the sub scope of expr, so that all will be replaced if replace all is selected.
 */
fun findOccurrences(expr: RsExpr): List<RsExpr> {
    val parent = expr.ancestorOrSelf<RsBlock>()
        ?: expr.ancestorOrSelf<RsItemElement>() // outside a function, try to find a parent
        ?: return emptyList()
    return findOccurrences(parent, expr)
}

fun findOccurrences(parent: RsElement, expr: RsExpr): List<RsExpr> {
    val visitor = object : PsiRecursiveElementVisitor() {
        val foundOccurrences = ArrayList<RsExpr>()
        override fun visitElement(element: PsiElement) {
            if (element is RsExpr && PsiEquivalenceUtil.areElementsEquivalent(expr, element)) {
                foundOccurrences.add(element)
            } else {
                super.visitElement(element)
            }
        }
    }
    parent.acceptChildren(visitor)
    return visitor.foundOccurrences
}

fun moveEditorToNameElement(editor: Editor, element: PsiElement?): RsPatBinding? {
    val newName = element?.findBinding()
    editor.caretModel.moveToOffset(newName?.identifier?.textRange?.startOffset ?: 0)
    return newName
}

fun PsiElement.findBinding() = PsiTreeUtil.findChildOfType(this, RsPatBinding::class.java)
