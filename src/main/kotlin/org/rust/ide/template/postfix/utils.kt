/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Condition
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.types.ty.TyBool
import org.rust.lang.core.types.type
import org.rust.lang.utils.negate

internal object RsPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun getNegatedExpression(element: PsiElement): PsiElement =
        element.negate()

    override fun createExpression(context: PsiElement, prefix: String, suffix: String): PsiElement =
        RsPsiFactory(context.project).createExpression("$prefix${context.text}$suffix")
}

class RsExprParentsSelector(pred: (RsExpr) -> Boolean = { true })
    : PostfixTemplateExpressionSelectorBase(Condition { it is RsExpr && pred(it) }) {

    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
        val expressions = super.getExpressions(context, document, offset)
        // `PostfixTemplateWithExpressionSelector#expand` selects only one item from this list in unit tests.
        // But in different platform versions different items are selected (of course, it's very convenient).
        // So let's return the latest item to commit tests behavior with all platform versions
        return if (isUnitTestMode) listOfNotNull(expressions.lastOrNull()) else expressions
    }

    override fun getNonFilteredExpressions(
        context: PsiElement,
        document: Document,
        offset: Int
    ): List<PsiElement> = context
        .ancestors
        .takeWhile { it !is RsBlock }
        .filter { it is RsExpr }
        .toList()
}

class RsTypeParentsSelector : PostfixTemplateExpressionSelectorBase(Condition { it is RsTypeReference }) {
    override fun getNonFilteredExpressions(
        context: PsiElement,
        document: Document,
        offset: Int
    ): List<PsiElement> = context
        .ancestors
        .filter { it is RsTypeReference }
        .toList()
}

fun RsExpr.isBool() = type == TyBool
