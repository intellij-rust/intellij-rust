/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.RsValueParameterList
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.leftSiblings
import org.rust.lang.core.psi.ext.rightSiblings

abstract class ChopListIntention<TList : RsElement, TElement : RsElement>(
    private val listClass: Class<TList>,
    private val elementClass: Class<TElement>,
    intentionText: String
) : RsElementBaseIntentionAction<TList>() {

    init {
        text = intentionText
    }

    override fun getFamilyName() = text

    private val PsiElement.listContext: TList?
        get() = PsiTreeUtil.getParentOfType(this, listClass, true)

    private val TList.elements: List<TElement>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, elementClass)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): TList? {
        val list = element.listContext ?: return null
        val elements = list.elements
        if (elements.size < 2 || elements.dropLast(1).all { hasLineBreakAfter(it) }) return null
        return list
    }

    override fun invoke(project: Project, editor: Editor, ctx: TList) {
        val document = editor.document
        val startOffset = ctx.textRange.startOffset

        val elements = ctx.elements
        val last = elements.last()
        if (!hasLineBreakAfter(last)) {
            last.textRange?.endOffset?.also { document.insertString(it, "\n") }
        }
        elements.asReversed().forEach {
            if (!hasLineBreakBefore(it)) {
                document.insertString(it.textRange.startOffset, "\n")
            }
        }
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitDocument(document)
        val psiFile = documentManager.getPsiFile(document)
        if (psiFile != null) {
            psiFile.findElementAt(startOffset)?.listContext?.also {
                CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, it.textRange)
            }
        }
    }

    private fun hasLineBreakAfter(element: TElement): Boolean {
        return element.rightSiblings.lineBreak() != null
    }

    private fun hasLineBreakBefore(element: TElement): Boolean {
        return element.leftSiblings.lineBreak() != null
    }

    private fun Sequence<PsiElement>.lineBreak(): PsiWhiteSpace? {
        return dropWhile { it !is PsiWhiteSpace && it !is PsiComment }
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .firstOrNull { it is PsiWhiteSpace && it.textContains('\n') } as? PsiWhiteSpace
    }
}

class ChopParameterListIntention : ChopListIntention<RsValueParameterList, RsValueParameter>(
    RsValueParameterList::class.java,
    RsValueParameter::class.java,
    "Put parameters on separate lines"
)
