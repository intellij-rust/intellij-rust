/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.RsValueParameterList
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

abstract class JoinListIntentionBase<TList : RsElement, TElement : RsElement>(
    listClass: Class<TList>,
    elementClass: Class<TElement>,
    intentionText: String
) : ListIntentionBase<TList, TElement>(listClass, elementClass, intentionText) {
    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): TList? {
        val list = element.listContext ?: return null
        val elements = list.elements
        if (elements.isEmpty() || !hasLineBreakBefore(elements.first()) && elements.none { hasLineBreakAfter(it) }) {
            return null
        }
        return list
    }

    override fun invoke(project: Project, editor: Editor, ctx: TList) {
        val document = editor.document
        val elements = ctx.elements

        nextBreak(elements.last())?.let { document.deleteString(it.startOffset, it.endOffset) }
        elements.dropLast(1).asReversed().forEach {
            nextBreak(it)?.let { document.replaceString(it.startOffset, it.endOffset, " ") }
        }
        prevBreak(elements.first())?.let { document.deleteString(it.startOffset, it.endOffset) }
    }
}

class JoinParameterListIntention : JoinListIntentionBase<RsValueParameterList, RsValueParameter>(
    RsValueParameterList::class.java,
    RsValueParameter::class.java,
    "Put parameters on one line"
)
