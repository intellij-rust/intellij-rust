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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.DOTDOT
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.startOffset

abstract class ChopListIntentionBase<TList : RsElement, TElement : RsElement>(
    listClass: Class<TList>,
    elementClass: Class<TElement>,
    intentionText: String
) : ListIntentionBase<TList, TElement>(listClass, elementClass, intentionText) {
    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): TList? {
        val list = element.listContext ?: return null
        val elements = getElements(list)
        if (elements.size < 2 || elements.dropLast(1).all { hasLineBreakAfter(list, it) }) return null
        return list
    }

    override fun invoke(project: Project, editor: Editor, ctx: TList) {
        val document = editor.document
        val startOffset = ctx.startOffset

        val elements = getElements(ctx)
        val last = elements.last()
        if (!hasLineBreakAfter(ctx, last)) {
            getEndElement(ctx, last).textRange?.endOffset?.also { document.insertString(it, "\n") }
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

class ChopFieldListIntention : ChopListIntentionBase<RsBlockFields, RsNamedFieldDecl>(
    RsBlockFields::class.java,
    RsNamedFieldDecl::class.java,
    "Put fields on separate lines"
)

/**
 * This intention has a special case for the dotdot (..) element.
 */
class ChopLiteralFieldListIntention : ChopListIntentionBase<RsStructLiteralBody, RsStructLiteralField>(
    RsStructLiteralBody::class.java,
    RsStructLiteralField::class.java,
    "Put fields on separate lines"
) {
    override fun getElements(context: RsStructLiteralBody): List<PsiElement> =
        super.getElements(context) + listOfNotNull(context.dotdot)

    override fun getEndElement(ctx: RsStructLiteralBody, element: PsiElement): PsiElement =
        when (element.elementType) {
            DOTDOT -> ctx.expr?.let { getEndElement(ctx, it) } ?: element
            else -> super.getEndElement(ctx, element)
        }
}

class ChopVariantListIntention : ChopListIntentionBase<RsEnumBody, RsEnumVariant>(
    RsEnumBody::class.java,
    RsEnumVariant::class.java,
    "Put variants on separate lines"
)
