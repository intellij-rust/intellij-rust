/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.RsValueParameterList
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.startOffset

abstract class ChopListIntentionBase<TList : RsElement, TElement : RsElement>(
    listClass: Class<TList>,
    elementClass: Class<TElement>,
    intentionText: String
) : ListIntentionBase<TList, TElement>(listClass, elementClass, intentionText) {
    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): TList? {
        val list = element.listContext ?: return null
        val elements = list.elements
        if (elements.size < 2 || elements.dropLast(1).all { hasLineBreakAfter(it) }) return null
        return list
    }

    override fun invoke(project: Project, editor: Editor, ctx: TList) {
        val document = editor.document
        val startOffset = ctx.startOffset

        val elements = ctx.elements
        val last = elements.last()
        if (!hasLineBreakAfter(last)) {
            last.textRange?.endOffset?.also { document.insertString(it, "\n") }
        }
        elements.asReversed().forEach {
            if (!hasLineBreakBefore(it)) {
                document.insertString(it.startOffset, "\n")
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
}

class ChopParameterListIntention : ChopListIntentionBase<RsValueParameterList, RsValueParameter>(
    RsValueParameterList::class.java,
    RsValueParameter::class.java,
    "Put parameters on separate lines"
)

class ChopArgumentListIntention : ChopListIntentionBase<RsValueArgumentList, RsExpr>(
    RsValueArgumentList::class.java,
    RsExpr::class.java,
    "Put arguments on separate lines"
)
