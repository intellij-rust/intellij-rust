/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.ext.*

abstract class ListIntentionBase<TList : RsElement, TElement : RsElement>(
    private val listClass: Class<TList>,
    private val elementClass: Class<TElement>,
    intentionText: String
) : RsElementBaseIntentionAction<TList>() {

    init {
        text = intentionText
    }

    override fun getFamilyName(): String = text

    protected val PsiElement.listContext: TList?
        get() = PsiTreeUtil.getParentOfType(this, listClass, true)

    protected val TList.elements: List<TElement>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, elementClass)

    protected fun hasLineBreakAfter(element: PsiElement): Boolean = nextBreak(element) != null

    protected fun nextBreak(element: PsiElement): PsiWhiteSpace? = element.rightSiblings.lineBreak()

    protected fun hasLineBreakBefore(element: PsiElement): Boolean = prevBreak(element) != null

    protected fun prevBreak(element: PsiElement): PsiWhiteSpace? = element.leftSiblings.lineBreak()

    protected fun commaAfter(element: PsiElement): PsiElement? =
        element.getNextNonCommentSibling()?.takeIf { it.elementType == COMMA }

    private fun Sequence<PsiElement>.lineBreak(): PsiWhiteSpace? =
        dropWhile { it !is PsiWhiteSpace && it !is PsiComment }
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .firstOrNull { it is PsiWhiteSpace && it.textContains('\n') } as? PsiWhiteSpace
}
