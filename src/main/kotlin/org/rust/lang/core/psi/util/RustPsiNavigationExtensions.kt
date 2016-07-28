package org.rust.lang.core.psi.util

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustExprElement

inline fun <reified T : PsiElement> PsiElement.parentOfType(strict: Boolean = true, minStartOffset: Int = -1): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(strict: Boolean = true): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, strict)

inline fun <reified T : PsiElement> PsiElement.descendentsOfType(): Collection<T> =
    PsiTreeUtil.findChildrenOfType(this, T::class.java)


/**
 * Finds first sibling that is neither comment, nor whitespace before given element.
 */
fun PsiElement?.getPrevNonCommentSibling(): PsiElement? =
    PsiTreeUtil.skipSiblingsBackward(this, PsiWhiteSpace::class.java, PsiComment::class.java)

/**
 * Finds first sibling that is neither comment, nor whitespace after given element.
 */
fun PsiElement?.getNextNonCommentSibling(): PsiElement? =
    PsiTreeUtil.skipSiblingsForward(this, PsiWhiteSpace::class.java, PsiComment::class.java)


/**
 * Finds top-most [RustExprElement] within selected range.
 * Supports such cases: `ident&lt;if&gt;ier` (or keyword if applicable),
 * `&lt;&nbsp;&nbsp;&nbsp;expression&nbsp;&nbsp;&nbsp;&gt;`.
 */
fun findExpressionInRange(file: PsiFile, startOffset: Int, endOffset: Int): RustExprElement? {
    val element1 = file.findElementAtIgnoreWhitespaceBefore(startOffset) ?: return null
    val element2 = file.findElementAtIgnoreWhitespaceAfter(endOffset - 1) ?: return null

    // Elements have crossed (for instance when selection was inside single whitespace block)
    if (element1.textRange.startOffset >= element2.textRange.endOffset) return null

    // Get common expression parent.
    var parent = PsiTreeUtil.findCommonParent(element1, element2) ?: return null
    if (parent !is RustExprElement) {
        parent = parent.parentOfType<RustExprElement>() ?: return null
    }

    // If our parent's deepest first child is element1 and deepest last - element 2,
    // then is is completely within selection, so this is our sought expression.
    if (element1 == PsiTreeUtil.getDeepestFirst(parent) && element2 == PsiTreeUtil.getDeepestLast(element2)) {
        return parent
    }

    return null
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
