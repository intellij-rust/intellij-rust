/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

abstract class JoinListIntentionBase<TList : RsElement, TElement : RsElement>(
    listClass: Class<TList>,
    elementClass: Class<TElement>,
    intentionText: String,
    private val prefix: String = "",
    private val suffix: String = ""
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
        nextBreak(elements.last())?.replace(prefix, document)
        elements.dropLast(1).asReversed().forEach { nextBreak(it)?.replace(" ", document) }
        prevBreak(elements.first())?.replace(suffix, document)
    }

    private fun PsiWhiteSpace.replace(replaceString: String, document: Document) {
        if (replaceString.isEmpty()) {
            document.deleteString(startOffset, endOffset)
        } else {
            document.replaceString(startOffset, endOffset, replaceString)
        }
    }
}

class JoinParameterListIntention : JoinListIntentionBase<RsValueParameterList, RsValueParameter>(
    RsValueParameterList::class.java,
    RsValueParameter::class.java,
    "Put parameters on one line"
)

class JoinArgumentListIntention : JoinListIntentionBase<RsValueArgumentList, RsExpr>(
    RsValueArgumentList::class.java,
    RsExpr::class.java,
    "Put arguments on one line"
)

class JoinFieldListIntention : JoinListIntentionBase<RsBlockFields, RsNamedFieldDecl>(
    RsBlockFields::class.java,
    RsNamedFieldDecl::class.java,
    "Put fields on one line",
    prefix = " ",
    suffix = " "
)

class JoinVariantListIntention : JoinListIntentionBase<RsEnumBody, RsEnumVariant>(
    RsEnumBody::class.java,
    RsEnumVariant::class.java,
    "Put variants on one line",
    prefix = " ",
    suffix = " "
)
