/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsStmt
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.ancestorOrSelf

fun findExpressionAtCaret(file: RsFile, offset: Int): RsExpr? {
    val expr = file.expressionAtOffset(offset)
    val exprBefore = file.expressionAtOffset(offset - 1)
    return when {
        expr == null -> exprBefore
        exprBefore == null -> expr
        PsiTreeUtil.isAncestor(expr, exprBefore, false) -> exprBefore
        else -> expr
    }
}

private fun RsFile.expressionAtOffset(offset: Int): RsExpr? =
    findElementAt(offset)?.ancestorOrSelf()


/**
 * Finds top-most [RsExpr] within selected range.
 * Supports such cases: `ident&lt;if&gt;ier` (or keyword if applicable),
 * `&lt;&nbsp;&nbsp;&nbsp;expression&nbsp;&nbsp;&nbsp;&gt;`.
 */
fun findExpressionInRange(file: PsiFile, startOffset: Int, endOffset: Int): RsExpr? {
    val (element1, element2) = file.getElementRange(startOffset, endOffset) ?: return null

    // Get common expression parent.
    var parent = PsiTreeUtil.findCommonParent(element1, element2) ?: return null
    parent = parent.ancestorOrSelf<RsExpr>() ?: return null

    // If our parent's deepest first child is element1 and deepest last - element 2,
    // then is is completely within selection, so this is our sought expression.
    if (element1 == PsiTreeUtil.getDeepestFirst(parent) && element2 == PsiTreeUtil.getDeepestLast(element2)) {
        return parent
    }

    return null
}

// FIXME: Maybe items are ok?
// FIXME: What about macros?
/**
 * Finds statements (mostly [RsStmt]s, [PsiComment]s and [RsExpr]
 * as return expression, doesn't allow attrs and items) within selected range.
 */
fun findStatementsInRange(file: PsiFile, startOffset: Int, endOffset: Int): Array<out PsiElement> {
    var (element1, element2) = file.getElementRange(startOffset, endOffset) ?: return emptyArray()

    // Find parent of selected statement list (syntactically only RsBlock is possible)
    val parent = PsiTreeUtil.findCommonParent(element1, element2)
        ?.ancestorOrSelf<RsBlock>()
        ?: return emptyArray()

    // Find edge direct children of parent within selection.
    // element1 has to be first leaf of left child, and element2 - last leaf of right child
    val realStartOffset = element1.textRange.startOffset
    val realEndOffset = element2.textRange.endOffset

    element1 = element1.getTopmostParentInside(parent)
    if (realStartOffset != element1.textRange.startOffset) return emptyArray()

    element2 = element2.getTopmostParentInside(parent)
    if (realEndOffset != element2.textRange.endOffset) return emptyArray()

    // Now collect non-whitespace children of parent between (inclusive) element1 and element2
    val elements = collectElements(element1, element2.nextSibling) { it !is PsiWhiteSpace }

    // Finally check if found elements meet requirements, and return result
    elements.forEachIndexed { idx, element ->
        if (!(element is RsStmt
            || (idx == elements.size - 1 && element is RsExpr)
            || element is PsiComment
            )) return emptyArray()
    }

    return elements
}


/**
 * Finds two edge leaf PSI elements within given range.
 */
fun PsiFile.getElementRange(startOffset: Int, endOffset: Int): Pair<PsiElement, PsiElement>? {
    val element1 = findElementAtIgnoreWhitespaceBefore(startOffset) ?: return null
    val element2 = findElementAtIgnoreWhitespaceAfter(endOffset - 1) ?: return null

    // Elements have crossed (for instance when selection was inside single whitespace block)
    if (element1.textRange.startOffset >= element2.textRange.endOffset) return null

    return element1 to element2
}

/**
 * Finds a leaf PSI element at the specified offset from the start of the text range of this node.
 * If found element is whitespace, returns its next non-whitespace sibling.
 */
fun PsiFile.findElementAtIgnoreWhitespaceBefore(offset: Int): PsiElement? {
    val element = findElementAt(offset)
    if (element is PsiWhiteSpace) {
        return findElementAt(element.getTextRange().endOffset)
    }
    return element
}

/**
 * Finds a leaf PSI element at the specified offset from the start of the text range of this node.
 * If found element is whitespace, returns its previous non-whitespace sibling.
 */
fun PsiFile.findElementAtIgnoreWhitespaceAfter(offset: Int): PsiElement? {
    val element = findElementAt(offset)
    if (element is PsiWhiteSpace) {
        return findElementAt(element.getTextRange().startOffset - 1)
    }
    return element
}

/**
 * Finds child of [parent] of which given element is descendant.
 */
private fun PsiElement.getTopmostParentInside(parent: PsiElement): PsiElement {
    if (parent == this) return this

    var element = this
    while (parent != element.parent) {
        element = element.parent
    }
    return element
}

private fun collectElements(start: PsiElement, stop: PsiElement?, pred: (PsiElement) -> Boolean): Array<out PsiElement> {
    check(stop == null || start.parent == stop.parent)

    val psiSeq = generateSequence(start) {
        if (it.nextSibling == stop)
            null
        else
            it.nextSibling
    }

    return PsiUtilCore.toPsiElementArray(psiSeq.filter(pred).toList())
}
