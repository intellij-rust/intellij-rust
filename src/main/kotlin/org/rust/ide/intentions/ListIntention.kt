/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.parser.RustParserDefinition.Companion.EOL_COMMENT
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

    protected open fun getElements(context: TList): List<PsiElement> =
        PsiTreeUtil.getChildrenOfTypeAsList(context, elementClass)

    protected open fun getEndElement(ctx: TList, element: PsiElement): PsiElement =
        commaAfter(element) ?: element

    protected fun hasLineBreakAfter(ctx: TList, element: PsiElement): Boolean =
        nextBreak(getEndElement(ctx, element)) != null

    protected fun nextBreak(element: PsiElement): PsiWhiteSpace? =
        element.rightSiblings.lineBreak()

    protected fun hasLineBreakBefore(element: PsiElement): Boolean =
        prevBreak(element) != null

    protected fun prevBreak(element: PsiElement): PsiWhiteSpace? =
        element.leftSiblings.lineBreak()

    protected fun hasEolComment(element: PsiElement): Boolean =
        element.descendantsOfType<PsiComment>().any { it.elementType == EOL_COMMENT }

    private fun commaAfter(element: PsiElement): PsiElement? =
        element.getNextNonCommentSibling()?.takeIf { it.elementType == COMMA }

    private fun Sequence<PsiElement>.lineBreak(): PsiWhiteSpace? =
        dropWhile { it !is PsiWhiteSpace && it !is PsiComment }
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .firstOrNull { it is PsiWhiteSpace && it.textContains('\n') } as? PsiWhiteSpace
}
